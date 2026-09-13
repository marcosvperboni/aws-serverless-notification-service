package com.marcosperboni.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marcosperboni.notification.exception.NotificationDispatchException;
import com.marcosperboni.notification.exception.NotificationNotFoundException;
import com.marcosperboni.notification.model.NotificationChannel;
import com.marcosperboni.notification.model.NotificationRecord;
import com.marcosperboni.notification.model.NotificationRequest;
import com.marcosperboni.notification.model.NotificationStatus;
import com.marcosperboni.notification.repository.NotificationRepository;
import com.marcosperboni.notification.service.provider.NotificationProvider;
import com.marcosperboni.notification.service.provider.NotificationProviderFactory;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	private static final int MAX_ATTEMPTS = 3;
	private static final String QUEUE_URL = "http://localhost:4566/000000000000/notifications-queue";

	@Mock
	private NotificationRepository repository;

	@Mock
	private SqsClient sqsClient;

	@Mock
	private NotificationProviderFactory providerFactory;

	@Mock
	private NotificationProvider provider;

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	private NotificationService service;

	@BeforeEach
	void setUp() {
		service = new NotificationService(repository, sqsClient, providerFactory, objectMapper, QUEUE_URL,
				MAX_ATTEMPTS);
	}

	@Test
	void submit_persistsThenEnqueuesThenMarksQueued() {
		NotificationRequest request = new NotificationRequest("user@example.com", NotificationChannel.EMAIL,
				"Subject", "Hello");

		String notificationId = service.submit(request);

		assertThat(notificationId).isNotBlank();
		verify(repository).save(any(NotificationRecord.class));
		verify(sqsClient).sendMessage(any(SendMessageRequest.class));
		verify(repository).updateStatus(eq(notificationId), eq(NotificationStatus.QUEUED), eq(0), isNull());
	}

	@Test
	void getById_unknownId_throwsNotFound() {
		when(repository.findById("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getById("missing")).isInstanceOf(NotificationNotFoundException.class);
	}

	@Test
	void process_successfulSend_marksSent() {
		NotificationRecord record = recordFor("id-1", NotificationChannel.EMAIL);
		when(repository.findById("id-1")).thenReturn(Optional.of(record));
		when(providerFactory.forChannel(NotificationChannel.EMAIL)).thenReturn(provider);

		service.process("id-1", 1);

		verify(provider).send(record);
		verify(repository).updateStatus("id-1", NotificationStatus.PROCESSING, 1, null);
		verify(repository).updateStatus("id-1", NotificationStatus.SENT, 1, null);
	}

	@Test
	void process_failureBelowMaxAttempts_marksFailedAndRethrows() {
		NotificationRecord record = recordFor("id-2", NotificationChannel.SMS);
		when(repository.findById("id-2")).thenReturn(Optional.of(record));
		when(providerFactory.forChannel(NotificationChannel.SMS)).thenReturn(provider);
		org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(provider).send(record);

		assertThatThrownBy(() -> service.process("id-2", 1)).isInstanceOf(NotificationDispatchException.class);

		verify(repository).updateStatus(eq("id-2"), eq(NotificationStatus.FAILED), eq(1), any());
		verify(repository, never()).updateStatus(eq("id-2"), eq(NotificationStatus.DEAD_LETTER), anyInt(), any());
	}

	@Test
	void process_failureAtMaxAttempts_marksDeadLetterAndRethrows() {
		NotificationRecord record = recordFor("id-3", NotificationChannel.PUSH);
		when(repository.findById("id-3")).thenReturn(Optional.of(record));
		when(providerFactory.forChannel(NotificationChannel.PUSH)).thenReturn(provider);
		org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(provider).send(record);

		assertThatThrownBy(() -> service.process("id-3", MAX_ATTEMPTS)).isInstanceOf(NotificationDispatchException.class);

		verify(repository, times(1)).updateStatus(eq("id-3"), eq(NotificationStatus.DEAD_LETTER),
				eq(MAX_ATTEMPTS), any());
	}

	private NotificationRecord recordFor(String id, NotificationChannel channel) {
		NotificationRecord record = new NotificationRecord();
		record.setNotificationId(id);
		record.setChannel(channel.name());
		record.setRecipient("target");
		record.setMessage("message");
		record.setStatus(NotificationStatus.QUEUED.name());
		return record;
	}
}
