package com.marcosperboni.notification.service.provider;

import com.marcosperboni.notification.exception.NotificationDispatchException;
import com.marcosperboni.notification.model.NotificationRecord;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class SmsNotificationProvider implements NotificationProvider {

	private final SnsClient snsClient;

	public SmsNotificationProvider(SnsClient snsClient) {
		this.snsClient = snsClient;
	}

	@Override
	public void send(NotificationRecord record) {
		PublishRequest request = PublishRequest.builder()
				.phoneNumber(record.getRecipient())
				.message(record.getMessage())
				.build();
		try {
			snsClient.publish(request);
		} catch (SdkException e) {
			throw new NotificationDispatchException("SNS SMS publish failed for " + record.getNotificationId(), e);
		}
	}
}
