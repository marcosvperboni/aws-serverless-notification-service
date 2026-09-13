package com.marcosperboni.notification.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.marcosperboni.notification.config.AwsClients;
import com.marcosperboni.notification.config.LambdaEnvironment;
import com.marcosperboni.notification.repository.NotificationRepository;
import com.marcosperboni.notification.service.NotificationQueueMessage;
import com.marcosperboni.notification.service.NotificationService;
import com.marcosperboni.notification.service.provider.EmailNotificationProvider;
import com.marcosperboni.notification.service.provider.NotificationProviderFactory;
import com.marcosperboni.notification.service.provider.PushNotificationProvider;
import com.marcosperboni.notification.service.provider.SmsNotificationProvider;
import tools.jackson.databind.ObjectMapper;

public class ProcessNotificationHandler implements RequestHandler<SQSEvent, Void> {

	private static final String RECEIVE_COUNT_ATTRIBUTE = "ApproximateReceiveCount";

	private final NotificationService notificationService;
	private final ObjectMapper objectMapper;

	public ProcessNotificationHandler() {
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
	}

	@Override
	public Void handleRequest(SQSEvent event, Context context) {
		for (SQSEvent.SQSMessage message : event.getRecords()) {
			try {
				NotificationQueueMessage queueMessage = objectMapper.readValue(message.getBody(),
						NotificationQueueMessage.class);
				int receiveCount = Integer.parseInt(
						message.getAttributes().getOrDefault(RECEIVE_COUNT_ATTRIBUTE, "1"));
				notificationService.process(queueMessage.notificationId(), receiveCount);
			} catch (Exception e) {
				context.getLogger().log("Failed to process SQS message " + message.getMessageId() + ": " + e);
				throw e instanceof RuntimeException runtimeException ? runtimeException : new RuntimeException(e);
			}
		}
		return null;
	}
}
