package com.marcosperboni.notification.service.provider;

import com.marcosperboni.notification.exception.NotificationDispatchException;
import com.marcosperboni.notification.model.NotificationRecord;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class PushNotificationProvider implements NotificationProvider {

	private final SnsClient snsClient;
	private final String topicArn;

	public PushNotificationProvider(SnsClient snsClient, String topicArn) {
		this.snsClient = snsClient;
		this.topicArn = topicArn;
	}

	@Override
	public void send(NotificationRecord record) {
		String subject = record.getSubject() != null ? record.getSubject() : "Notification";
		PublishRequest request = PublishRequest.builder()
				.topicArn(topicArn)
				.subject(subject)
				.message(record.getMessage())
				.build();
		try {
			snsClient.publish(request);
		} catch (SdkException e) {
			throw new NotificationDispatchException("SNS push publish failed for " + record.getNotificationId(), e);
		}
	}
}
