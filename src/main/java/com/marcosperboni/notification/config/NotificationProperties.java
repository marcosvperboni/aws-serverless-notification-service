package com.marcosperboni.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
		String tableName,
		String queueUrl,
		String dlqUrl,
		String sesSenderAddress,
		String pushTopicArn,
		int maxProcessingAttempts) {
}
