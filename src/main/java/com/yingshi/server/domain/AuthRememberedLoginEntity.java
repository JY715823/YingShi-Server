package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "auth_remembered_logins",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_auth_remembered_logins_user_device", columnNames = {"user_id", "device_id"})
        },
        indexes = {
                @Index(name = "idx_auth_remembered_logins_user_id", columnList = "user_id"),
                @Index(name = "idx_auth_remembered_logins_account", columnList = "account"),
                @Index(name = "idx_auth_remembered_logins_expire_at", columnList = "expire_at")
        }
)
public class AuthRememberedLoginEntity extends BaseEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, length = 120)
    private String account;

    @Column(nullable = false, length = 160)
    private String deviceId;

    @Column(nullable = false)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expireAt;

    @Column(nullable = false)
    private Instant lastAuthenticatedAt;

    @Column(nullable = false)
    private Instant lastUsedAt;

    @Column
    private Instant revokedAt;

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

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Instant expireAt) {
        this.expireAt = expireAt;
    }

    public Instant getLastAuthenticatedAt() {
        return lastAuthenticatedAt;
    }

    public void setLastAuthenticatedAt(Instant lastAuthenticatedAt) {
        this.lastAuthenticatedAt = lastAuthenticatedAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
