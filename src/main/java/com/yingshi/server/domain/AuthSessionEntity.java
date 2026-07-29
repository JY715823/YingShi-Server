package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "auth_sessions")
public class AuthSessionEntity extends BaseEntity {

    @Id
    private String id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String libraryId;

    @Column(nullable = false)
    private String refreshTokenId;

    @Column(nullable = false)
    private Instant refreshExpireAt;

    @Column(nullable = false)
    private Instant lastAuthenticatedAt;

    @Column
    private Instant revokedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
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

    public String getRefreshTokenId() {
        return refreshTokenId;
    }

    public void setRefreshTokenId(String refreshTokenId) {
        this.refreshTokenId = refreshTokenId;
    }

    public Instant getRefreshExpireAt() {
        return refreshExpireAt;
    }

    public void setRefreshExpireAt(Instant refreshExpireAt) {
        this.refreshExpireAt = refreshExpireAt;
    }

    public Instant getLastAuthenticatedAt() {
        return lastAuthenticatedAt;
    }

    public void setLastAuthenticatedAt(Instant lastAuthenticatedAt) {
        this.lastAuthenticatedAt = lastAuthenticatedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
