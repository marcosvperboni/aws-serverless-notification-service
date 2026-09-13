package com.marcosperboni.notification.config;

import com.marcosperboni.notification.repository.NotificationRepository;
import com.marcosperboni.notification.service.NotificationService;
import com.marcosperboni.notification.service.provider.EmailNotificationProvider;
import com.marcosperboni.notification.service.provider.NotificationProviderFactory;
import com.marcosperboni.notification.service.provider.PushNotificationProvider;
import com.marcosperboni.notification.service.provider.SmsNotificationProvider;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationBeanConfiguration {

	@Bean
	public NotificationRepository notificationRepository(DynamoDbEnhancedClient enhancedClient,
			NotificationProperties properties) {
		return new NotificationRepository(enhancedClient, properties.tableName());
	}

	@Bean
	public EmailNotificationProvider emailNotificationProvider(SesClient sesClient, NotificationProperties properties) {
		return new EmailNotificationProvider(sesClient, properties.sesSenderAddress());
	}

	@Bean
	public SmsNotificationProvider smsNotificationProvider(SnsClient snsClient) {
		return new SmsNotificationProvider(snsClient);
	}

	@Bean
	public PushNotificationProvider pushNotificationProvider(SnsClient snsClient, NotificationProperties properties) {
		return new PushNotificationProvider(snsClient, properties.pushTopicArn());
	}

	@Bean
	public NotificationProviderFactory notificationProviderFactory(EmailNotificationProvider email,
			SmsNotificationProvider sms, PushNotificationProvider push) {
		return new NotificationProviderFactory(email, sms, push);
	}

	@Bean
	public NotificationService notificationService(NotificationRepository repository, SqsClient sqsClient,
			NotificationProviderFactory providerFactory, ObjectMapper objectMapper, NotificationProperties properties) {
		return new NotificationService(repository, sqsClient, providerFactory, objectMapper, properties.queueUrl(),
				properties.maxProcessingAttempts());
	}
}
