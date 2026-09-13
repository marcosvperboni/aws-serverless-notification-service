package com.marcosperboni.notification.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.marcosperboni.notification.exception.NotificationDispatchException;
import com.marcosperboni.notification.exception.NotificationNotFoundException;
import com.marcosperboni.notification.model.ApiError;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.reduce((a, b) -> a + "; " + b)
				.orElse("Validation failed");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of("VALIDATION_ERROR", message));
	}

	@ExceptionHandler(NotificationNotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(NotificationNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("NOT_FOUND", ex.getMessage()));
	}

	@ExceptionHandler(NotificationDispatchException.class)
	public ResponseEntity<ApiError> handleDispatchFailure(NotificationDispatchException ex) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiError.of("DISPATCH_FAILED", ex.getMessage()));
	}
}
