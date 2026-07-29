package com.yingshi.server.domain.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * R2-A: Read waterline tracking per (userId, libraryId).
 *
 * <p>Maps the {@code notification_read_waterline} table created by V44.
 * Stores the BIGSERIAL {@code id} of the most recent event the user has
 * marked as read (as a string, since the column is VARCHAR(255)). Events
 * with {@code id <= lastReadEventId} are considered read without needing
 * a per-row entry in {@code notification_reads}.
 *
 * <p>Uses {@link IdClass} for the composite primary key (user_id, library_id).
 */
@Entity
@Table(name = "notification_read_waterline")
@IdClass(NotificationReadWaterlineEntity.PK.class)
public class NotificationReadWaterlineEntity {

    @Id
    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Id
    @Column(name = "library_id", nullable = false, length = 255)
    private String libraryId;

    /**
     * BIGSERIAL id of the last read event, stored as a string (column is VARCHAR(255)).
     * Parsed back to Long when comparing against {@code NotificationEventEntity.id}.
     */
    @Column(name = "last_read_event_id", length = 255)
    private String lastReadEventId;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getLibraryId() {
        return libraryId;
    }

    public void setLibraryId(String libraryId) {
        this.libraryId = libraryId;
    }

    public String getLastReadEventId() {
        return lastReadEventId;
    }

    public void setLastReadEventId(String lastReadEventId) {
        this.lastReadEventId = lastReadEventId;
    }

    public Instant getLastReadAt() {
        return lastReadAt;
    }

    public void setLastReadAt(Instant lastReadAt) {
        this.lastReadAt = lastReadAt;
    }

    /**
     * Parse {@link #lastReadEventId} to a Long for numeric comparison.
     * Returns null if the value is blank or non-numeric.
     */
    public Long lastReadEventIdAsLong() {
        if (lastReadEventId == null || lastReadEventId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(lastReadEventId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Composite primary key for the waterline table. */
    public static class PK implements Serializable {
        private String userId;
        private String libraryId;

        public PK() {
        }

        public PK(String userId, String libraryId) {
            this.userId = userId;
            this.libraryId = libraryId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getLibraryId() {
            return libraryId;
        }

        public void setLibraryId(String libraryId) {
            this.libraryId = libraryId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK other)) return false;
            return Objects.equals(userId, other.userId)
                    && Objects.equals(libraryId, other.libraryId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, libraryId);
        }
    }
}
