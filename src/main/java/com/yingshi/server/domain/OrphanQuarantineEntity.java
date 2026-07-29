package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * R2-D-3/4: 孤儿对象 quarantine 隔离记录。
 * 扫描发现孤儿对象时只写入本表（不立即删除），隔离 quarantineUntil 后由 purgeQuarantined 真正删除。
 * UNIQUE(media_id, object_key) 防止重复隔离。
 */
@Entity
@Table(
        name = "orphan_quarantine",
        uniqueConstraints = @UniqueConstraint(name = "uk_orphan_quarantine_media_obj", columnNames = {"media_id", "object_key"})
)
public class OrphanQuarantineEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_id", length = 255)
    private String mediaId;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "quarantine_until", nullable = false)
    private Instant quarantineUntil;

    @Column(nullable = false, length = 32)
    private String status = "QUARANTINED";

    public OrphanQuarantineEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMediaId() {
        return mediaId;
    }

    public void setMediaId(String mediaId) {
        this.mediaId = mediaId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }

    public Instant getQuarantineUntil() {
        return quarantineUntil;
    }

    public void setQuarantineUntil(Instant quarantineUntil) {
        this.quarantineUntil = quarantineUntil;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
