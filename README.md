# AWS Serverless Notification Service

A serverless notification platform on AWS: an API accepts notification requests from other systems and dispatches them by email, SMS, or push, with full delivery history and status tracking.

## Architecture

```
Client ──POST /notifications──▶ API Gateway ──▶ Lambda (Ingest) ──▶ DynamoDB (persist RECEIVED)
                                                        │
                                                        ▼
                                                  SQS (notifications-queue)
                                                        │
                                                        ▼
                                             Lambda (Processor) ──▶ SES / SNS (email, SMS, push)
                                                        │
                                                        ▼
                                             DynamoDB (status update)

Failed messages, after max receive count ──▶ SQS DLQ (notifications-dlq)

Client ──GET /notifications/{id}──▶ API Gateway ──▶ Lambda (Ingest) ──▶ DynamoDB (read)
```

## Tech stack

| Concern | Technology |
|---|---|
| Language / runtime | Java 25 |
| Local dev framework | Spring Boot 4.0.8 (REST controller only, not used in the deployed Lambdas) |
| API | Amazon API Gateway (HTTP API) |
| Compute | AWS Lambda (two functions: ingest, processor) |
| Messaging | Amazon SQS + dead-letter queue |
| Notification delivery | Amazon SES (email), Amazon SNS (SMS, push) |
| Persistence | Amazon DynamoDB (AWS SDK v2 Enhanced Client) |
| Access control | IAM (least-privilege, per-function roles) |
| Observability | Amazon CloudWatch Logs |
| Infrastructure as code | Terraform (`hashicorp/aws` provider) |
| Local AWS emulation | LocalStack + Podman Compose |
| Build | Maven (`maven-shade-plugin` for the Lambda artifact, `spring-boot-maven-plugin` for local dev) |
| CI | GitHub Actions (build + test on every push/PR) |

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/notifications` | Accepts a notification request, returns `202` with the generated id |
| `GET` | `/notifications/{id}` | Returns the full record for one notification, `404` if unknown |
| `GET` | `/notifications?status=SENT` | Lists notifications, optionally filtered by status |

`POST /notifications` body:

```json
{
  "recipient": "user@example.com",
  "channel": "EMAIL",
  "subject": "Welcome",
  "message": "Thanks for signing up"
}
```

`channel` is one of `EMAIL`, `SMS`, `PUSH`. `subject` is optional (SMS/push ignore it).

## Local development

1. Start LocalStack (creates the `notifications` table, `notifications-queue` + `notifications-dlq`, and the push SNS topic on boot):

   ```bash
   podman compose up -d
   ```

2. Run the Spring Boot dev server (uses the `AWS_ENDPOINT_URL`-driven LocalStack defaults baked into `application.yml`):

   ```bash
   ./mvnw spring-boot:run
   ```

3. Send a notification:

   ```bash
   curl -X POST http://localhost:8080/notifications \
     -H "Content-Type: application/json" \
     -d '{"recipient":"user@example.com","channel":"EMAIL","subject":"Hi","message":"Hello there"}'
   ```

4. Check its status:

   ```bash
   curl http://localhost:8080/notifications/<id-from-step-3>
   curl "http://localhost:8080/notifications?status=SENT"
   ```

Since LocalStack's Lambda/API Gateway services aren't wired up by this compose file (the local dev loop uses the Spring controller instead — see design decisions below), SQS processing only happens once you also run `ProcessNotificationHandler` yourself against LocalStack, or you point the LocalStack Lambda service at the built `-aws.jar` and an SQS event source mapping if you want to exercise the real Lambda path end to end locally.

## Building the Lambda deployment artifact

```bash
./mvnw clean package
```

Produces two jars under `target/`:

- `aws-serverless-notification-service-0.0.1-SNAPSHOT.jar` — Spring Boot repackaged jar, for local dev (`java -jar` or `spring-boot:run`).
- `aws-serverless-notification-service-0.0.1-SNAPSHOT-aws.jar` — flat shaded jar with a Lambda-correct classpath, for deployment to AWS Lambda.

## Deploying the infrastructure

The Terraform in `infra/terraform/` provisions every AWS resource in the diagram above (DynamoDB table + GSI, SQS queue + DLQ, SNS topic, two Lambda functions, HTTP API, IAM roles, CloudWatch log groups). It is **not applied by this repository** — no AWS credentials are provisioned in the environment this was built in.

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # fill in your values (region, SES sender identity, etc.)
terraform init
terraform plan
terraform apply
```

## Project structure

```
src/main/java/com/marcosperboni/notification/
  AwsServerlessNotificationServiceApplication.java   Spring Boot entry point (local dev only)
  model/        NotificationRequest, NotificationRecord, NotificationResponse,
                NotificationSubmittedResponse, NotificationChannel, NotificationStatus, ApiError
  repository/   NotificationRepository (DynamoDB Enhanced Client)
  service/      NotificationService, NotificationQueueMessage
    provider/   NotificationProvider, NotificationProviderFactory,
                EmailNotificationProvider, SmsNotificationProvider, PushNotificationProvider
  handler/      IngestNotificationHandler, ProcessNotificationHandler (deployed Lambda entry points)
  controller/   NotificationController, GlobalExceptionHandler (local dev only)
  config/       AwsClients, AwsClientConfiguration, NotificationBeanConfiguration,
                NotificationProperties, LambdaEnvironment
  exception/    NotificationNotFoundException, NotificationDispatchException

infra/terraform/   DynamoDB, SQS+DLQ, SNS, IAM, Lambda, API Gateway, CloudWatch, in separate files
scripts/            LocalStack init hook (table/queue/topic bootstrap)
docker-compose.yml  LocalStack for local dev (run with `podman compose`)
.github/workflows/  CI: build + test on every push/PR
```

## Design decisions

**Plain Lambda handlers, not Spring inside Lambda.** Running a full Spring context on every cold start is the classic anti-pattern for latency-sensitive Lambda functions. `IngestNotificationHandler` and `ProcessNotificationHandler` implement `RequestHandler` directly with manual, no-arg-constructor dependency wiring — no Spring on the classpath's hot path. The Spring Boot app (`NotificationController`) is a local-dev-only façade over the exact same `service`/`repository`/`provider` classes, so there is no duplicated business logic between the two entry points; only the adapters differ. A package-private secondary constructor on `IngestNotificationHandler` accepts pre-built dependencies purely so unit tests can inject mocks without paying for real AWS SDK client construction.

**DynamoDB item is a plain JavaBean, not a Lombok class.** The AWS SDK v2 Enhanced Client's `@DynamoDbBean` introspection reads annotations (`@DynamoDbPartitionKey`, `@DynamoDbSecondaryPartitionKey`) off the getter methods themselves. Lombok does not copy custom annotations onto its generated getters without extra `lombok.config` wiring, so `NotificationRecord` is written by hand to keep the partition key and GSI wiring unambiguous and safe from a class of subtle runtime failures. `channel`/`status` are stored as their enum name and `createdAt`/`updatedAt` as ISO-8601 strings, since the Enhanced Client has no built-in `java.time.Instant` converter.

**Lambda jar excludes the AWS SDK's default async Netty client.** Every AWS SDK client used here (DynamoDB, SQS, SNS, SES) is called synchronously, but each service module pulls in `netty-nio-client` by default. That's dozens of extra classes bundled into the exact jar this project is trying to keep lean for cold starts, so the `pom.xml` excludes it per SDK dependency and adds `url-connection-client` instead, wired explicitly in `AwsClients`.

**Shaded jar must be built before `spring-boot:repackage` runs.** Both the shade plugin and `spring-boot-maven-plugin` bind to the `package` phase; `spring-boot-maven-plugin` rewrites the main jar in place into its nested `BOOT-INF/` loader layout. The shade plugin is declared first in `pom.xml` so it shades the still-flat jar (default jar:jar always runs before either), producing a Lambda-correct flat classpath; `spring-boot-maven-plugin` then repackages the main artifact afterward for local dev, without affecting the already-attached `-aws` classified jar.

**SQS batch failures retry the whole batch.** `ProcessNotificationHandler` does not implement `ReportBatchItemFailures` partial-batch reporting — a failure on any message in a batch causes Lambda's SQS trigger to retry the entire batch, the simplest and default behavior. Combined with `receiveCount` tracking, once a message's `ApproximateReceiveCount` reaches the configured max attempts, its DynamoDB record is proactively marked `DEAD_LETTER` (even though it's SQS's own redrive policy, not this code, that actually moves the message to the DLQ). Partial-batch failure reporting and delivery idempotency keys are reasonable next steps if duplicate-send risk under retry needs tightening.

**Push notifications: a real gap, not faked.** `PushNotificationProvider` performs a genuine `sns:Publish` against a configured SNS topic ARN. Real per-device mobile push (APNs/FCM) requires an SNS Platform Application plus a platform endpoint ARN registered per device, which in turn requires Apple/Google push credentials this project does not have. Wiring individual devices means subscribing platform endpoints to this topic (or publishing to a `targetArn` endpoint instead of the topic) once a platform application exists — that setup step is out of scope here and is called out explicitly rather than stubbed out silently.

**IAM: SNS `Publish` to a phone number has no ARN to scope.** The processor role's SMS-publish statement uses `Resource: "*"`, which is the only option AWS's own IAM reference gives for this specific action/target combination (a destination phone number is not an ARN-addressable resource). Every other statement — DynamoDB table + GSI, the specific SQS queue, the specific SNS push topic, the SES sender identity, and each function's own CloudWatch log group — is scoped to its exact resource ARN.

**Lambda runtime is `java25` (configurable).** If the target AWS region's Lambda service has not yet added a `java25` managed runtime by the time this is deployed, override `lambda_runtime` in `terraform.tfvars` to the newest available Java runtime and recompile with a matching `--release` version — a Java 25 class file cannot run on an older JVM runtime.

**LocalStack pinned to `3.8`, not `latest`.** Newer LocalStack images gate startup behind a `LOCALSTACK_AUTH_TOKEN` license check even for services that used to be free. `docker-compose.yml` pins a Community-edition version that starts without any token, verified end to end against this project's DynamoDB/SQS/SNS usage.

## Testing

`./mvnw clean verify` runs the full suite with Mockito mocks — no Podman, no LocalStack, no AWS credentials required:

- `NotificationServiceTest` — persist → enqueue → status transition flow, including the `FAILED` vs `DEAD_LETTER` branch at max attempts.
- `NotificationProviderFactoryTest` — correct provider resolved per channel.
- `NotificationControllerTest` — MockMvc: validation → `400`, happy path → `202`, unknown id → `404`.
- `IngestNotificationHandlerTest` — the same contract exercised through the actual Lambda handler class.

CI (`.github/workflows/ci.yml`) runs this same build on every push and pull request.

## License

MIT — see [LICENSE](LICENSE).
