# AWS Serverless Notification Service

Uma plataforma serverless de notificações na AWS: uma API recebe solicitações de notificação de outros sistemas e realiza o envio por e-mail, SMS ou push, com histórico completo de entregas e consulta de status.

## Arquitetura

```
Cliente ──POST /notifications──▶ API Gateway ──▶ Lambda (Ingest) ──▶ DynamoDB (grava RECEIVED)
                                                        │
                                                        ▼
                                                  SQS (notifications-queue)
                                                        │
                                                        ▼
                                             Lambda (Processor) ──▶ SES / SNS (email, SMS, push)
                                                        │
                                                        ▼
                                             DynamoDB (atualiza status)

Mensagens com falha, após o número máximo de tentativas ──▶ SQS DLQ (notifications-dlq)

Cliente ──GET /notifications/{id}──▶ API Gateway ──▶ Lambda (Ingest) ──▶ DynamoDB (leitura)
```

## Tecnologias

| Camada | Tecnologia |
|---|---|
| Linguagem / runtime | Java 25 |
| Framework de desenvolvimento local | Spring Boot 4.0.8 (apenas o controller REST, não é usado nas Lambdas em produção) |
| API | Amazon API Gateway (HTTP API) |
| Computação | AWS Lambda (duas funções: ingest, processor) |
| Mensageria | Amazon SQS + fila de mensagens mortas (DLQ) |
| Envio de notificações | Amazon SES (e-mail), Amazon SNS (SMS, push) |
| Persistência | Amazon DynamoDB (AWS SDK v2 Enhanced Client) |
| Controle de acesso | IAM (privilégio mínimo, uma role por função) |
| Observabilidade | Amazon CloudWatch Logs |
| Infraestrutura como código | Terraform (provider `hashicorp/aws`) |
| Emulação local da AWS | LocalStack + Podman Compose |
| Build | Maven (`maven-shade-plugin` para o artefato Lambda, `spring-boot-maven-plugin` para desenvolvimento local) |
| CI | GitHub Actions (build + testes a cada push/PR) |

## API

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/notifications` | Recebe uma solicitação de notificação, retorna `202` com o id gerado |
| `GET` | `/notifications/{id}` | Retorna o registro completo de uma notificação, `404` se não existir |
| `GET` | `/notifications?status=SENT` | Lista notificações, com filtro opcional por status |

Corpo do `POST /notifications`:

```json
{
  "recipient": "user@example.com",
  "channel": "EMAIL",
  "subject": "Welcome",
  "message": "Thanks for signing up"
}
```

`channel` é um dos valores `EMAIL`, `SMS`, `PUSH`. `subject` é opcional (SMS/push ignoram esse campo).

## Desenvolvimento local

1. Suba o LocalStack (cria a tabela `notifications`, as filas `notifications-queue` + `notifications-dlq` e o tópico SNS de push na inicialização):

   ```bash
   podman compose up -d
   ```

2. Rode o servidor de desenvolvimento Spring Boot (usa os defaults do LocalStack já configurados em `application.yml`, via `AWS_ENDPOINT_URL`):

   ```bash
   ./mvnw spring-boot:run
   ```

3. Envie uma notificação:

   ```bash
   curl -X POST http://localhost:8080/notifications \
     -H "Content-Type: application/json" \
     -d '{"recipient":"user@example.com","channel":"EMAIL","subject":"Hi","message":"Hello there"}'
   ```

4. Consulte o status:

   ```bash
   curl http://localhost:8080/notifications/<id-do-passo-3>
   curl "http://localhost:8080/notifications?status=SENT"
   ```

Como os serviços de Lambda/API Gateway do LocalStack não estão conectados neste compose (o fluxo local usa o controller Spring no lugar — ver decisões de projeto abaixo), o processamento da fila SQS só acontece se você também rodar o `ProcessNotificationHandler` manualmente contra o LocalStack, ou apontar o serviço Lambda do LocalStack para o `-aws.jar` gerado com um event source mapping do SQS, caso queira exercitar o caminho real das Lambdas de ponta a ponta localmente.

## Gerando o artefato de deploy da Lambda

```bash
./mvnw clean package
```

Gera dois jars em `target/`:

- `aws-serverless-notification-service-0.0.1-SNAPSHOT.jar` — jar reempacotado pelo Spring Boot, para desenvolvimento local (`java -jar` ou `spring-boot:run`).
- `aws-serverless-notification-service-0.0.1-SNAPSHOT-aws.jar` — jar shaded plano, com classpath correto para deploy na AWS Lambda.

## Deploy da infraestrutura

O Terraform em `infra/terraform/` provisiona todos os recursos AWS do diagrama acima (tabela DynamoDB + GSI, fila SQS + DLQ, tópico SNS, duas funções Lambda, HTTP API, roles IAM, grupos de log do CloudWatch). Ele **não é aplicado por este repositório** — este ambiente de desenvolvimento não possui credenciais AWS.

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # preencha seus valores (região, identidade de remetente SES, etc.)
terraform init
terraform plan
terraform apply
```

## Estrutura do projeto

```
src/main/java/com/marcosperboni/notification/
  AwsServerlessNotificationServiceApplication.java   ponto de entrada Spring Boot (apenas dev local)
  model/        NotificationRequest, NotificationRecord, NotificationResponse,
                NotificationSubmittedResponse, NotificationChannel, NotificationStatus, ApiError
  repository/   NotificationRepository (DynamoDB Enhanced Client)
  service/      NotificationService, NotificationQueueMessage
    provider/   NotificationProvider, NotificationProviderFactory,
                EmailNotificationProvider, SmsNotificationProvider, PushNotificationProvider
  handler/      IngestNotificationHandler, ProcessNotificationHandler (entry points das Lambdas em produção)
  controller/   NotificationController, GlobalExceptionHandler (apenas dev local)
  config/       AwsClients, AwsClientConfiguration, NotificationBeanConfiguration,
                NotificationProperties, LambdaEnvironment
  exception/    NotificationNotFoundException, NotificationDispatchException

infra/terraform/   DynamoDB, SQS+DLQ, SNS, IAM, Lambda, API Gateway, CloudWatch, em arquivos separados
scripts/            hook de inicialização do LocalStack (cria tabela/filas/tópico)
docker-compose.yml  LocalStack para desenvolvimento local (execute com `podman compose`)
.github/workflows/  CI: build + testes a cada push/PR
```

## Decisões de projeto

**Lambdas puras, sem Spring rodando dentro da Lambda.** Manter um contexto Spring completo em todo cold start é o anti-padrão clássico para funções Lambda sensíveis a latência. `IngestNotificationHandler` e `ProcessNotificationHandler` implementam `RequestHandler` diretamente, com injeção manual de dependências via construtor sem argumentos — sem Spring no caminho quente do classpath. A aplicação Spring Boot (`NotificationController`) é uma fachada usada apenas em desenvolvimento local sobre as mesmas classes de `service`/`repository`/`provider`, então não há lógica de negócio duplicada entre os dois pontos de entrada; só os adaptadores mudam. Um construtor secundário package-private em `IngestNotificationHandler` aceita dependências já construídas apenas para permitir que os testes unitários injetem mocks sem pagar o custo de criar clientes reais do AWS SDK.

**O item do DynamoDB é um JavaBean simples, não uma classe Lombok.** A introspecção do `@DynamoDbBean` do AWS SDK v2 Enhanced Client lê as anotações (`@DynamoDbPartitionKey`, `@DynamoDbSecondaryPartitionKey`) diretamente nos métodos getters. O Lombok não copia anotações customizadas para os getters que ele gera, a menos que se configure `lombok.config` adicionalmente, então `NotificationRecord` foi escrito manualmente para deixar a chave de partição e o GSI sem ambiguidade e livres dessa classe de falha sutil em tempo de execução. `channel`/`status` são armazenados pelo nome do enum e `createdAt`/`updatedAt` como strings ISO-8601, já que o Enhanced Client não tem conversor nativo para `java.time.Instant`.

**O jar da Lambda exclui o cliente assíncrono Netty padrão do AWS SDK.** Todos os clientes AWS SDK usados aqui (DynamoDB, SQS, SNS, SES) são chamados de forma síncrona, mas cada módulo de serviço traz o `netty-nio-client` por padrão. São dezenas de classes extras empacotadas no jar que este projeto tenta manter enxuto para reduzir cold start, então o `pom.xml` exclui esse cliente em cada dependência do SDK e adiciona o `url-connection-client` no lugar, conectado explicitamente em `AwsClients`.

**O jar shaded precisa ser gerado antes do `spring-boot:repackage` rodar.** Tanto o plugin de shade quanto o `spring-boot-maven-plugin` são vinculados à fase `package`; o `spring-boot-maven-plugin` reescreve o jar principal no lugar, transformando-o na estrutura aninhada `BOOT-INF/`. O plugin de shade é declarado primeiro no `pom.xml` para empacotar o jar ainda plano (o `jar:jar` padrão sempre roda antes de qualquer um dos dois), gerando um classpath plano correto para a Lambda; o `spring-boot-maven-plugin` então reempacota o artefato principal depois, para uso em desenvolvimento local, sem afetar o jar `-aws` que já foi anexado.

**Falhas em lote no SQS reprocessam o lote inteiro.** `ProcessNotificationHandler` não implementa o relatório parcial de falhas (`ReportBatchItemFailures`) — uma falha em qualquer mensagem do lote faz o trigger SQS da Lambda reprocessar o lote inteiro, o comportamento padrão mais simples. Combinado ao rastreamento de `receiveCount`, quando o `ApproximateReceiveCount` de uma mensagem atinge o número máximo de tentativas configurado, o registro correspondente no DynamoDB é marcado proativamente como `DEAD_LETTER` (mesmo sendo a própria política de redrive do SQS, e não este código, quem efetivamente move a mensagem para a DLQ). Relatório parcial de falhas em lote e chaves de idempotência de envio são evoluções razoáveis caso o risco de envio duplicado em retentativas precise ser reduzido.

**Notificações push: uma lacuna real, não simulada.** `PushNotificationProvider` realiza um `sns:Publish` de verdade contra um ARN de tópico SNS configurado. Push móvel real por dispositivo (APNs/FCM) exige uma SNS Platform Application mais um ARN de endpoint de plataforma registrado por dispositivo, o que por sua vez exige credenciais de push da Apple/Google que este projeto não possui. Conectar dispositivos individuais significa inscrever endpoints de plataforma neste tópico (ou publicar em um endpoint `targetArn` em vez do tópico) assim que uma platform application existir — essa etapa de configuração está fora do escopo aqui e é apontada explicitamente, em vez de ser simulada silenciosamente.

**IAM: `Publish` do SNS para um número de telefone não tem ARN para restringir.** A statement de envio de SMS da role do processor usa `Resource: "*"`, que é a única opção que a própria referência de IAM da AWS oferece para essa combinação específica de ação/destino (um número de telefone de destino não é um recurso endereçável por ARN). Todas as outras statements — tabela DynamoDB + GSI, a fila SQS específica, o tópico SNS de push específico, a identidade de remetente do SES e o grupo de log do CloudWatch de cada função — são restritas ao ARN exato do recurso.

**O runtime da Lambda é `java25` (configurável).** Se o serviço Lambda da região AWS de destino ainda não tiver adicionado um runtime gerenciado `java25` no momento do deploy, troque a variável `lambda_runtime` no `terraform.tfvars` para o runtime Java mais recente disponível e recompile com a versão `--release` correspondente — um `.class` compilado para Java 25 não roda em uma JVM mais antiga.

**LocalStack fixado na versão `3.8`, e não `latest`.** Imagens mais recentes do LocalStack passaram a exigir um `LOCALSTACK_AUTH_TOKEN` de licença mesmo para serviços que antes eram gratuitos. O `docker-compose.yml` fixa uma versão da edição Community que inicia sem exigir nenhum token, validada de ponta a ponta com o uso de DynamoDB/SQS/SNS deste projeto.

## Testes

`./mvnw clean verify` executa toda a suíte com mocks do Mockito — sem necessidade de Podman, LocalStack ou credenciais AWS:

- `NotificationServiceTest` — fluxo completo de gravação → enfileiramento → transição de status, incluindo o desvio entre `FAILED` e `DEAD_LETTER` no número máximo de tentativas.
- `NotificationProviderFactoryTest` — provider correto é resolvido para cada canal.
- `NotificationControllerTest` — MockMvc: validação → `400`, caminho feliz → `202`, id inexistente → `404`.
- `IngestNotificationHandlerTest` — o mesmo contrato exercitado através da classe real da Lambda.

O CI (`.github/workflows/ci.yml`) executa esse mesmo build a cada push e pull request.

## Licença

MIT — veja [LICENSE](LICENSE).
