package com.marcosperboni.notification.service.provider;

import java.util.Map;

import com.marcosperboni.notification.model.NotificationChannel;

public class NotificationProviderFactory {

	private final Map<NotificationChannel, NotificationProvider> providers;

	public NotificationProviderFactory(EmailNotificationProvider email, SmsNotificationProvider sms,
			PushNotificationProvider push) {
		this.providers = Map.of(
				NotificationChannel.EMAIL, email,
				NotificationChannel.SMS, sms,
				NotificationChannel.PUSH, push);
	}

	public NotificationProvider forChannel(NotificationChannel channel) {
		NotificationProvider provider = providers.get(channel);
		if (provider == null) {
			throw new IllegalArgumentException("Unsupported notification channel: " + channel);
		}
		return provider;
	}
}
