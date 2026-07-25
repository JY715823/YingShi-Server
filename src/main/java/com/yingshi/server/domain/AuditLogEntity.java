package com.yingshi.server.domain;

import com.yingshi.server.common.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Audit log entry for critical operations.
 *
 * <p>Records who did what, to which resource, from where. Does NOT contain
 * sensitive data (passwords, tokens, full request bodies).
 *
 * <p>Table: audit_logs (created by V37 migration)
 */
@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    private static final String ID_PREFIX = "audit_";

    @Id
    @Column(name = "id", length = 48, nullable = false, updatable = false)
    private String id;

    @Column(name = "actor_user_id", length = 48)
    private String actorUserId;

    @Column(name = "library_id", length = 48)
    private String libraryId;

    /** Action type: LOGIN, LOGOUT, UPLOAD, DELETE, IMPORT, etc. */
    @Column(name = "action", length = 64, nullable = false)
    private String action;

    /** Resource type: media, album, trash_item, chat, etc. */
    @Column(name = "resource_type", length = 64)
    private String resourceType;

    /** Resource ID (if applicable). */
    @Column(name = "resource_id", length = 128)
    private String resourceId;

    /** Non-sensitive details (JSON or plain text). */
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 256)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void ensureId() {
        if (id == null || id.isBlank()) {
            id = IdGenerator.newId(ID_PREFIX);
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getActorUserId() { return actorUserId; }
    public void setActorUserId(String actorUserId) { this.actorUserId = actorUserId; }
    public String getLibraryId() { return libraryId; }
    public void setLibraryId(String libraryId) { this.libraryId = libraryId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
