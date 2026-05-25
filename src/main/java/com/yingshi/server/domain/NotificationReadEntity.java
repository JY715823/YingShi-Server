package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "notification_reads",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_read_user_notification",
                columnNames = {"userId", "notificationId"}
        )
)
public class NotificationReadEntity extends BaseEntity {

    @Id
    private String id;

    @Column(nullable = false, length = 255)
    private String userId;

    @Column(nullable = false, length = 255)
    private String notificationId;

    @Column(nullable = false)
    private Instant readAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }
}
