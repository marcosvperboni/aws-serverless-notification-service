package com.marcosperboni.notification.model;

import java.time.Instant;

public record NotificationResponse(
		String notificationId,
		NotificationChannel channel,
		String recipient,
		String subject,
		String message,
		NotificationStatus status,
		int attempts,
		String errorMessage,
		Instant createdAt,
		Instant updatedAt) {

	public static NotificationResponse from(NotificationRecord record) {
		return new NotificationResponse(
				record.getNotificationId(),
				NotificationChannel.valueOf(record.getChannel()),
				record.getRecipient(),
				record.getSubject(),
				record.getMessage(),
				NotificationStatus.valueOf(record.getStatus()),
				record.getAttempts(),
				record.getErrorMessage(),
				Instant.parse(record.getCreatedAt()),
				Instant.parse(record.getUpdatedAt()));
	}
}
