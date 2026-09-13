package com.marcosperboni.notification.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.marcosperboni.notification.exception.NotificationNotFoundException;
import com.marcosperboni.notification.model.NotificationChannel;
import com.marcosperboni.notification.model.NotificationRecord;
import com.marcosperboni.notification.model.NotificationStatus;
import com.marcosperboni.notification.service.NotificationService;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private NotificationService notificationService;

	@Test
	void create_validRequest_returns202WithId() throws Exception {
		when(notificationService.submit(org.mockito.ArgumentMatchers.any())).thenReturn("notif-1");

		mockMvc.perform(post("/notifications")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"recipient":"user@example.com","channel":"EMAIL","subject":"Hi","message":"Hello"}
								"""))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.notificationId", is("notif-1")))
				.andExpect(jsonPath("$.status", is("QUEUED")));
	}

	@Test
	void create_missingMessage_returns400() throws Exception {
		mockMvc.perform(post("/notifications")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"recipient":"user@example.com","channel":"EMAIL"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
	}

	@Test
	void getById_unknownId_returns404() throws Exception {
		when(notificationService.getById("missing")).thenThrow(new NotificationNotFoundException("missing"));

		mockMvc.perform(get("/notifications/missing"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code", is("NOT_FOUND")));
	}

	@Test
	void getById_knownId_returns200() throws Exception {
		NotificationRecord record = new NotificationRecord();
		record.setNotificationId("notif-1");
		record.setChannel(NotificationChannel.EMAIL.name());
		record.setRecipient("user@example.com");
		record.setSubject("Hi");
		record.setMessage("Hello");
		record.setStatus(NotificationStatus.SENT.name());
		record.setAttempts(1);
		record.setCreatedAt(Instant.now().toString());
		record.setUpdatedAt(Instant.now().toString());
		when(notificationService.getById("notif-1")).thenReturn(record);

		mockMvc.perform(get("/notifications/notif-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.notificationId", is("notif-1")))
				.andExpect(jsonPath("$.status", is("SENT")));
	}

	@Test
	void list_returnsAllNotifications() throws Exception {
		when(notificationService.list(null)).thenReturn(List.of());

		mockMvc.perform(get("/notifications")).andExpect(status().isOk());
	}
}
