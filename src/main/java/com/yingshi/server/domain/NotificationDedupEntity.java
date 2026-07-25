package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persistent notification dedup entry.
 * Replaces in-memory ConcurrentHashMap for cross-restart and multi-instance dedup.
 * Table: notification_dedup (created by V38 migration)
 */
@Entity
@Table(name = "notification_dedup")
public class NotificationDedupEntity {

    @Id
    @Column(name = "operation_key", length = 255, nullable = false)
    private String operationKey;

    @Column(name = "library_id", length = 48, nullable = false)
    private String libraryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public NotificationDedupEntity() {}

    public NotificationDedupEntity(String operationKey, String libraryId) {
        this.operationKey = operationKey;
        this.libraryId = libraryId;
        this.createdAt = Instant.now();
    }

    public String getOperationKey() { return operationKey; }
    public void setOperationKey(String operationKey) { this.operationKey = operationKey; }
    public String getLibraryId() { return libraryId; }
    public void setLibraryId(String libraryId) { this.libraryId = libraryId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
