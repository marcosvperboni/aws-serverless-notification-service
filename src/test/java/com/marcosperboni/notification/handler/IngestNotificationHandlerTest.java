package com.marcosperboni.notification.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.marcosperboni.notification.exception.NotificationNotFoundException;
import com.marcosperboni.notification.model.NotificationChannel;
import com.marcosperboni.notification.model.NotificationRecord;
import com.marcosperboni.notification.model.NotificationStatus;
import com.marcosperboni.notification.service.NotificationService;

import jakarta.validation.Validation;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class IngestNotificationHandlerTest {

	@Mock
	private NotificationService notificationService;

	private IngestNotificationHandler handler;

	@BeforeEach
	void setUp() {
		handler = new IngestNotificationHandler(notificationService, JsonMapper.builder().build(),
				Validation.buildDefaultValidatorFactory().getValidator());
	}

	@Test
	void create_validBody_returns202WithId() {
		when(notificationService.submit(org.mockito.ArgumentMatchers.any())).thenReturn("notif-1");

		APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
				.withHttpMethod("POST")
				.withBody("{\"recipient\":\"user@example.com\",\"channel\":\"EMAIL\",\"message\":\"hi\"}");

		APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

		assertThat(response.getStatusCode()).isEqualTo(202);
		assertThat(response.getBody()).contains("notif-1").contains("QUEUED");
	}

	@Test
	void create_missingRequiredField_returns400() {
		APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
				.withHttpMethod("POST")
				.withBody("{\"recipient\":\"user@example.com\",\"channel\":\"EMAIL\"}");

		APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

		assertThat(response.getStatusCode()).isEqualTo(400);
	}

	@Test
	void getById_unknownId_returns404() {
		when(notificationService.getById("missing")).thenThrow(new NotificationNotFoundException("missing"));

		APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
				.withHttpMethod("GET")
				.withPathParameters(Map.of("id", "missing"));

		APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

		assertThat(response.getStatusCode()).isEqualTo(404);
	}

	@Test
	void getById_knownId_returns200() {
		NotificationRecord record = new NotificationRecord();
		record.setNotificationId("notif-1");
		record.setChannel(NotificationChannel.EMAIL.name());
		record.setRecipient("user@example.com");
		record.setMessage("hi");
		record.setStatus(NotificationStatus.SENT.name());
		record.setCreatedAt(java.time.Instant.now().toString());
		record.setUpdatedAt(java.time.Instant.now().toString());
		when(notificationService.getById("notif-1")).thenReturn(record);

		APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
				.withHttpMethod("GET")
				.withPathParameters(Map.of("id", "notif-1"));

		APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

		assertThat(response.getStatusCode()).isEqualTo(200);
		assertThat(response.getBody()).contains("notif-1");
	}
}
