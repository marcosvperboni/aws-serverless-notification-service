package com.marcosperboni.notification.service.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.marcosperboni.notification.model.NotificationChannel;

class NotificationProviderFactoryTest {

	private final EmailNotificationProvider email = mock(EmailNotificationProvider.class);
	private final SmsNotificationProvider sms = mock(SmsNotificationProvider.class);
	private final PushNotificationProvider push = mock(PushNotificationProvider.class);
	private final NotificationProviderFactory factory = new NotificationProviderFactory(email, sms, push);

	@Test
	void forChannel_returnsMatchingProvider() {
		assertThat(factory.forChannel(NotificationChannel.EMAIL)).isSameAs(email);
		assertThat(factory.forChannel(NotificationChannel.SMS)).isSameAs(sms);
		assertThat(factory.forChannel(NotificationChannel.PUSH)).isSameAs(push);
	}
}
