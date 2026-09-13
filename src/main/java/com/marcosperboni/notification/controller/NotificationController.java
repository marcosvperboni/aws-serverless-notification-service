package com.marcosperboni.notification.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marcosperboni.notification.model.NotificationRequest;
import com.marcosperboni.notification.model.NotificationResponse;
import com.marcosperboni.notification.model.NotificationStatus;
import com.marcosperboni.notification.model.NotificationSubmittedResponse;
import com.marcosperboni.notification.service.NotificationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@PostMapping
	public ResponseEntity<NotificationSubmittedResponse> create(@Valid @RequestBody NotificationRequest request) {
		String notificationId = notificationService.submit(request);
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(new NotificationSubmittedResponse(notificationId, NotificationStatus.QUEUED));
	}

	@GetMapping("/{id}")
	public NotificationResponse getById(@PathVariable("id") String id) {
		return NotificationResponse.from(notificationService.getById(id));
	}

	@GetMapping
	public List<NotificationResponse> list(@RequestParam(name = "status", required = false) NotificationStatus status) {
		return notificationService.list(status).stream().map(NotificationResponse::from).toList();
	}
}
