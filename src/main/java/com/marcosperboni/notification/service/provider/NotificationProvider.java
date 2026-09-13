package com.marcosperboni.notification.service.provider;

import com.marcosperboni.notification.model.NotificationRecord;

public interface NotificationProvider {

	void send(NotificationRecord record);
}
