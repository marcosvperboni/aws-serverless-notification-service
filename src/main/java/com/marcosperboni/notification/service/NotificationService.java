package com.marcosperboni.notification.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.marcosperboni.notification.exception.NotificationDispatchException;
import com.marcosperboni.notification.exception.NotificationNotFoundException;
import com.marcosperboni.notification.model.NotificationChannel;
import com.marcosperboni.notification.model.NotificationRecord;
import com.marcosperboni.notification.model.NotificationRequest;
import com.marcosperboni.notification.model.NotificationStatus;
import com.marcosperboni.notification.repository.NotificationRepository;
import com.marcosperboni.notification.service.provider.NotificationProvider;
import com.marcosperboni.notification.service.provider.NotificationProviderFactory;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public class NotificationService {

	private final NotificationRepository repository;
	private final SqsClient sqsClient;
	private final NotificationProviderFactory providerFactory;
	private final ObjectMapper objectMapper;
	private final String queueUrl;
	private final int maxAttempts;

	public NotificationService(NotificationRepository repository, SqsClient sqsClient,
			NotificationProviderFactory providerFactory, ObjectMapper objectMapper, String queueUrl,
			int maxAttempts) {
		this.repository = repository;
		this.sqsClient = sqsClient;
		this.providerFactory = providerFactory;
		this.objectMapper = objectMapper;
		this.queueUrl = queueUrl;
		this.maxAttempts = maxAttempts;
	}

	public String submit(NotificationRequest request) {
		String notificationId = UUID.randomUUID().toString();
		String now = Instant.now().toString();

		NotificationRecord record = new NotificationRecord();
		record.setNotificationId(notificationId);
		record.setChannel(request.channel().name());
		record.setRecipient(request.recipient());
		record.setSubject(request.subject());
		record.setMessage(request.message());
		record.setStatus(NotificationStatus.RECEIVED.name());
		record.setAttempts(0);
		record.setCreatedAt(now);
		record.setUpdatedAt(now);
		repository.save(record);

		enqueue(notificationId);
		repository.updateStatus(notificationId, NotificationStatus.QUEUED, 0, null);
		log.info("Notification {} received and queued", notificationId);
		return notificationId;
	}

	private void enqueue(String notificationId) {
		try {
			String body = objectMapper.writeValueAsString(new NotificationQueueMessage(notificationId));
			sqsClient.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(body).build());
		} catch (JacksonException | SdkException e) {
			throw new NotificationDispatchException("Failed to enqueue notification " + notificationId, e);
		}
	}

	public NotificationRecord getById(String notificationId) {
		return repository.findById(notificationId)
				.orElseThrow(() -> new NotificationNotFoundException(notificationId));
	}

	public List<NotificationRecord> list(NotificationStatus statusFilter) {
		return repository.list(statusFilter);
	}

	public void process(String notificationId, int receiveCount) {
		NotificationRecord record = getById(notificationId);
		repository.updateStatus(notificationId, NotificationStatus.PROCESSING, receiveCount, null);

		NotificationProvider provider = providerFactory.forChannel(NotificationChannel.valueOf(record.getChannel()));
		try {
			provider.send(record);
			repository.updateStatus(notificationId, NotificationStatus.SENT, receiveCount, null);
			log.info("Notification {} sent on attempt {}", notificationId, receiveCount);
		} catch (RuntimeException e) {
			NotificationStatus failureStatus = receiveCount >= maxAttempts
					? NotificationStatus.DEAD_LETTER
					: NotificationStatus.FAILED;
			repository.updateStatus(notificationId, failureStatus, receiveCount, e.getMessage());
			log.warn("Notification {} failed on attempt {} ({})", notificationId, receiveCount, failureStatus);
			throw new NotificationDispatchException("Failed to dispatch notification " + notificationId, e);
		}
	}
}
