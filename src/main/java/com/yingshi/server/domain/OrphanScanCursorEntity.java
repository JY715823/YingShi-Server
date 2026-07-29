package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * R2-D-1/2: 孤儿扫描持久 cursor。
 * 单行表 (id='default')，记录上次扫描到的 (updatedAt, mediaId) 位置，
 * 下次扫描从此处继续；全表扫完后重置为 null 以便从头开始。
 */
@Entity
@Table(name = "orphan_scan_cursor")
public class OrphanScanCursorEntity extends BaseEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "last_scanned_updated_at")
    private Instant lastScannedUpdatedAt;

    @Column(name = "last_scanned_id", length = 64)
    private String lastScannedId;

    public OrphanScanCursorEntity() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getLastScannedUpdatedAt() {
        return lastScannedUpdatedAt;
    }

    public void setLastScannedUpdatedAt(Instant lastScannedUpdatedAt) {
        this.lastScannedUpdatedAt = lastScannedUpdatedAt;
    }

    public String getLastScannedId() {
        return lastScannedId;
    }

    public void setLastScannedId(String lastScannedId) {
        this.lastScannedId = lastScannedId;
    }
}
