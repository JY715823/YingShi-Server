package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bowel_events")
public class BowelEventEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Long occurredAtMillis;

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

    public Long getOccurredAtMillis() {
        return occurredAtMillis;
    }

    public void setOccurredAtMillis(Long occurredAtMillis) {
        this.occurredAtMillis = occurredAtMillis;
    }
}
