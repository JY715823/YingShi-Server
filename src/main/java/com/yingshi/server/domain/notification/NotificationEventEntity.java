package com.yingshi.server.domain.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * R2-A-1: Persistent notification event record.
 *
 * Stored in {@code notification_events} table with 30-day retention.
 * Used by SSE replay (SseEmitterRegistry) so missed events can be
 * redelivered even after a server restart, instead of relying on the
 * in-memory ring buffer which is lost on restart.
 */
@Entity
@Table(name = "notification_events")
public class NotificationEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 255)
    private String eventId;

    @Column(name = "library_id", nullable = false, length = 255)
    private String libraryId;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(nullable = false, length = 64)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String data;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (expiresAt == null) {
            expiresAt = now.plus(30, ChronoUnit.DAYS);
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getLibraryId() {
        return libraryId;
    }

    public void setLibraryId(String libraryId) {
        this.libraryId = libraryId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
