package com.marcosperboni.notification.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationRequest(
		@NotBlank(message = "recipient is required") String recipient,
		@NotNull(message = "channel is required") NotificationChannel channel,
		String subject,
		@NotBlank(message = "message is required") String message) {
}
