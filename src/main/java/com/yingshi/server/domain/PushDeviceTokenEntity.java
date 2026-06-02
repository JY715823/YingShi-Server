package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "push_device_tokens")
public class PushDeviceTokenEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, length = 512, unique = true)
    private String token;

    @Column(nullable = false, length = 40)
    private String platform;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private Long lastSeenAtMillis;

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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Long getLastSeenAtMillis() {
        return lastSeenAtMillis;
    }

    public void setLastSeenAtMillis(Long lastSeenAtMillis) {
        this.lastSeenAtMillis = lastSeenAtMillis;
    }
}
