package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * R3-TRASH-002: Outbox entity for transactional object storage deletion.
 * Within a DB transaction, only the intent is recorded. After commit,
 * PurgeIntentProcessor asynchronously deletes the referenced objects with retries.
 */
@Entity
@Table(name = "purge_intents")
public class PurgeIntentEntity extends BaseEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "trash_item_id", nullable = false, length = 64)
    private String trashItemId;

    @Column(name = "library_id", nullable = false, length = 64)
    private String libraryId;

    @Column(name = "object_type", nullable = false, length = 32)
    private String objectType;

    @Column(name = "storage_path", length = 512)
    private String storagePath;

    @Column(name = "object_key", length = 512)
    private String objectKey;

    @Column(name = "media_id", length = 64)
    private String mediaId;

    @Column(nullable = false, length = 32)
    private String state = "PENDING";

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 5;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "completed_at")
    private Instant completedAt;

    public PurgeIntentEntity() {
    }

    public PurgeIntentEntity(
            String id,
            String trashItemId,
            String libraryId,
            String objectType,
            String storagePath,
            String objectKey,
            String mediaId,
            String state,
            Integer attempts,
            Integer maxAttempts,
            Instant nextRetryAt,
            String lastError,
            Instant completedAt
    ) {
        this.id = id;
        this.trashItemId = trashItemId;
        this.libraryId = libraryId;
        this.objectType = objectType;
        this.storagePath = storagePath;
        this.objectKey = objectKey;
        this.mediaId = mediaId;
        this.state = state;
        this.attempts = attempts;
        this.maxAttempts = maxAttempts;
        this.nextRetryAt = nextRetryAt;
        this.lastError = lastError;
        this.completedAt = completedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTrashItemId() {
        return trashItemId;
    }

    public void setTrashItemId(String trashItemId) {
        this.trashItemId = trashItemId;
    }

    public String getLibraryId() {
        return libraryId;
    }

    public void setLibraryId(String libraryId) {
        this.libraryId = libraryId;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getMediaId() {
        return mediaId;
    }

    public void setMediaId(String mediaId) {
        this.mediaId = mediaId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
