package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "auth_login_challenges",
        indexes = {
                @Index(name = "idx_auth_login_challenges_account", columnList = "account"),
                @Index(name = "idx_auth_login_challenges_user_id", columnList = "user_id"),
                @Index(name = "idx_auth_login_challenges_expire_at", columnList = "expire_at")
        }
)
public class AuthLoginChallengeEntity extends BaseEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, length = 120)
    private String account;

    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private Instant expireAt;

    @Column(nullable = false)
    private Instant resendAvailableAt;

    @Column(nullable = false)
    private Instant lastSentAt;

    @Column(nullable = false)
    private int sendCount;

    @Column(nullable = false)
    private int failedAttempts;

    @Column
    private Instant consumedAt;

    @Column
    private Instant invalidatedAt;

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

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Instant expireAt) {
        this.expireAt = expireAt;
    }

    public Instant getResendAvailableAt() {
        return resendAvailableAt;
    }

    public void setResendAvailableAt(Instant resendAvailableAt) {
        this.resendAvailableAt = resendAvailableAt;
    }

    public Instant getLastSentAt() {
        return lastSentAt;
    }

    public void setLastSentAt(Instant lastSentAt) {
        this.lastSentAt = lastSentAt;
    }

    public int getSendCount() {
        return sendCount;
    }

    public void setSendCount(int sendCount) {
        this.sendCount = sendCount;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }

    public void setInvalidatedAt(Instant invalidatedAt) {
        this.invalidatedAt = invalidatedAt;
    }
}
