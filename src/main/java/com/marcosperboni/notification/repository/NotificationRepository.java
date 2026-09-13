package com.marcosperboni.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.marcosperboni.notification.exception.NotificationNotFoundException;
import com.marcosperboni.notification.model.NotificationRecord;
import com.marcosperboni.notification.model.NotificationStatus;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

public class NotificationRepository {

	private static final String STATUS_INDEX = "status-index";

	private final DynamoDbTable<NotificationRecord> table;
	private final DynamoDbIndex<NotificationRecord> statusIndex;

	public NotificationRepository(DynamoDbEnhancedClient enhancedClient, String tableName) {
		this.table = enhancedClient.table(tableName, TableSchema.fromBean(NotificationRecord.class));
		this.statusIndex = table.index(STATUS_INDEX);
	}

	public void save(NotificationRecord record) {
		table.putItem(record);
	}

	public Optional<NotificationRecord> findById(String notificationId) {
		return Optional.ofNullable(table.getItem(Key.builder().partitionValue(notificationId).build()));
	}

	public NotificationRecord updateStatus(String notificationId, NotificationStatus status, int attempts,
			String errorMessage) {
		NotificationRecord record = findById(notificationId)
				.orElseThrow(() -> new NotificationNotFoundException(notificationId));
		record.setStatus(status.name());
		record.setAttempts(attempts);
		record.setErrorMessage(errorMessage);
		record.setUpdatedAt(java.time.Instant.now().toString());
		table.putItem(record);
		return record;
	}

	public List<NotificationRecord> list(NotificationStatus statusFilter) {
		if (statusFilter == null) {
			return table.scan().items().stream().collect(Collectors.toList());
		}
		QueryConditional condition = QueryConditional
				.keyEqualTo(Key.builder().partitionValue(statusFilter.name()).build());
		return statusIndex.query(condition).stream()
				.flatMap(page -> page.items().stream())
				.collect(Collectors.toList());
	}
}
