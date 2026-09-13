package com.marcosperboni.notification.service.provider;

import com.marcosperboni.notification.exception.NotificationDispatchException;
import com.marcosperboni.notification.model.NotificationRecord;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

public class EmailNotificationProvider implements NotificationProvider {

	private final SesClient sesClient;
	private final String senderAddress;

	public EmailNotificationProvider(SesClient sesClient, String senderAddress) {
		this.sesClient = sesClient;
		this.senderAddress = senderAddress;
	}

	@Override
	public void send(NotificationRecord record) {
		String subject = record.getSubject() != null ? record.getSubject() : "Notification";
		SendEmailRequest request = SendEmailRequest.builder()
				.source(senderAddress)
				.destination(Destination.builder().toAddresses(record.getRecipient()).build())
				.message(Message.builder()
						.subject(Content.builder().data(subject).build())
						.body(Body.builder().text(Content.builder().data(record.getMessage()).build()).build())
						.build())
				.build();
		try {
			sesClient.sendEmail(request);
		} catch (SdkException e) {
			throw new NotificationDispatchException("SES send failed for " + record.getNotificationId(), e);
		}
	}
}
