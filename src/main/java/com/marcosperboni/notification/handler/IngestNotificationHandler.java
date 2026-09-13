package com.marcosperboni.notification.handler;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.marcosperboni.notification.config.AwsClients;
import com.marcosperboni.notification.config.LambdaEnvironment;
import com.marcosperboni.notification.exception.NotificationDispatchException;
import com.marcosperboni.notification.exception.NotificationNotFoundException;
import com.marcosperboni.notification.model.ApiError;
import com.marcosperboni.notification.model.NotificationRequest;
import com.marcosperboni.notification.model.NotificationResponse;
import com.marcosperboni.notification.model.NotificationStatus;
import com.marcosperboni.notification.model.NotificationSubmittedResponse;
import com.marcosperboni.notification.repository.NotificationRepository;
import com.marcosperboni.notification.service.NotificationService;
import com.marcosperboni.notification.service.provider.EmailNotificationProvider;
import com.marcosperboni.notification.service.provider.NotificationProviderFactory;
import com.marcosperboni.notification.service.provider.PushNotificationProvider;
import com.marcosperboni.notification.service.provider.SmsNotificationProvider;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import tools.jackson.databind.ObjectMapper;

public class IngestNotificationHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private final NotificationService notificationService;
	private final ObjectMapper objectMapper;
	private final Validator validator;

	public IngestNotificationHandler() {
		AwsClients awsClients = new AwsClients();
		NotificationRepository repository = new NotificationRepository(awsClients.dynamoDbEnhancedClient(),
				LambdaEnvironment.TABLE_NAME);
		NotificationProviderFactory providerFactory = new NotificationProviderFactory(
				new EmailNotificationProvider(awsClients.sesClient(), LambdaEnvironment.SES_SENDER_ADDRESS),
				new SmsNotificationProvider(awsClients.snsClient()),
				new PushNotificationProvider(awsClients.snsClient(), LambdaEnvironment.PUSH_TOPIC_ARN));
		this.objectMapper = new ObjectMapper();
		this.notificationService = new NotificationService(repository, awsClients.sqsClient(), providerFactory,
				objectMapper, LambdaEnvironment.QUEUE_URL, LambdaEnvironment.MAX_PROCESSING_ATTEMPTS);
		this.validator = Validation.buildDefaultValidatorFactory().getValidator();
	}

	IngestNotificationHandler(NotificationService notificationService, ObjectMapper objectMapper, Validator validator) {
		this.notificationService = notificationService;
		this.objectMapper = objectMapper;
		this.validator = validator;
	}

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
		try {
			String method = input.getHttpMethod();
			Map<String, String> pathParameters = input.getPathParameters();

			if ("POST".equalsIgnoreCase(method)) {
				return create(input.getBody());
			}
			if ("GET".equalsIgnoreCase(method) && pathParameters != null && pathParameters.get("id") != null) {
				return getById(pathParameters.get("id"));
			}
			if ("GET".equalsIgnoreCase(method)) {
				return list(input.getQueryStringParameters());
			}
			return respond(404, ApiError.of("NOT_FOUND", "Route not found"));
		} catch (NotificationNotFoundException e) {
			return respond(404, ApiError.of("NOT_FOUND", e.getMessage()));
		} catch (NotificationDispatchException e) {
			return respond(502, ApiError.of("DISPATCH_FAILED", e.getMessage()));
		} catch (Exception e) {
			context.getLogger().log("Unhandled error processing request: " + e);
			return respond(500, ApiError.of("INTERNAL_ERROR", "Unexpected error"));
		}
	}

	private APIGatewayProxyResponseEvent create(String body) {
		NotificationRequest request;
		try {
			request = objectMapper.readValue(body, NotificationRequest.class);
		} catch (Exception e) {
			return respond(400, ApiError.of("VALIDATION_ERROR", "Malformed request body"));
		}

		Set<ConstraintViolation<NotificationRequest>> violations = validator.validate(request);
		if (!violations.isEmpty()) {
			String message = violations.stream()
					.map(v -> v.getPropertyPath() + ": " + v.getMessage())
					.collect(Collectors.joining("; "));
			return respond(400, ApiError.of("VALIDATION_ERROR", message));
		}

		String notificationId = notificationService.submit(request);
		return respond(202, new NotificationSubmittedResponse(notificationId, NotificationStatus.QUEUED));
	}

	private APIGatewayProxyResponseEvent getById(String id) {
		NotificationResponse response = NotificationResponse.from(notificationService.getById(id));
		return respond(200, response);
	}

	private APIGatewayProxyResponseEvent list(Map<String, String> queryStringParameters) {
		NotificationStatus statusFilter = null;
		if (queryStringParameters != null && queryStringParameters.get("status") != null) {
			statusFilter = NotificationStatus.valueOf(queryStringParameters.get("status").toUpperCase());
		}
		var responses = notificationService.list(statusFilter).stream().map(NotificationResponse::from).toList();
		return respond(200, responses);
	}

	private APIGatewayProxyResponseEvent respond(int statusCode, Object body) {
		try {
			return new APIGatewayProxyResponseEvent()
					.withStatusCode(statusCode)
					.withHeaders(Map.of("Content-Type", "application/json"))
					.withBody(objectMapper.writeValueAsString(body));
		} catch (Exception e) {
			return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("{\"code\":\"INTERNAL_ERROR\"}");
		}
	}
}
