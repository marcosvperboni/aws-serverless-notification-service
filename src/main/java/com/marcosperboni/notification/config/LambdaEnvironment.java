package com.marcosperboni.notification.config;

public final class LambdaEnvironment {

	public static final String TABLE_NAME = env("NOTIFICATION_TABLE_NAME", "notifications");
	public static final String QUEUE_URL = env("NOTIFICATION_QUEUE_URL",
			"http://localhost:4566/000000000000/notifications-queue");
	public static final String SES_SENDER_ADDRESS = env("NOTIFICATION_SES_SENDER", "no-reply@notifications.local");
	public static final String PUSH_TOPIC_ARN = env("NOTIFICATION_PUSH_TOPIC_ARN",
			"arn:aws:sns:us-east-1:000000000000:notifications-push");
	public static final int MAX_PROCESSING_ATTEMPTS = Integer.parseInt(env("NOTIFICATION_MAX_ATTEMPTS", "3"));

	private LambdaEnvironment() {
	}

	private static String env(String key, String defaultValue) {
		String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}
}
