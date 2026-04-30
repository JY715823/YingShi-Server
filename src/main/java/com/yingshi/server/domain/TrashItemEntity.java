package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "trash_items")
public class TrashItemEntity extends SpaceScopedEntity {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TrashItemType itemType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TrashItemState state;

    @Column
    private String sourcePostId;

    @Column
    private String sourceMediaId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255)
    private String previewInfo;

    @Column(length = 2000)
    private String relatedPostIds;

    @Column(length = 2000)
    private String relatedMediaIds;

    @Lob
    @Column(nullable = false)
    private String snapshotJson;

    @Column(nullable = false)
    private Instant deletedAt;

    @Column
    private Instant removedAt;

    @Column
    private Instant undoDeadlineAt;

    @Column
    private Instant restoredAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public TrashItemType getItemType() {
        return itemType;
    }

    public void setItemType(TrashItemType itemType) {
        this.itemType = itemType;
    }

    public TrashItemState getState() {
        return state;
    }

    public void setState(TrashItemState state) {
        this.state = state;
    }

    public String getSourcePostId() {
        return sourcePostId;
    }

    public void setSourcePostId(String sourcePostId) {
        this.sourcePostId = sourcePostId;
    }

    public String getSourceMediaId() {
        return sourceMediaId;
    }

    public void setSourceMediaId(String sourceMediaId) {
        this.sourceMediaId = sourceMediaId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPreviewInfo() {
        return previewInfo;
    }

    public void setPreviewInfo(String previewInfo) {
        this.previewInfo = previewInfo;
    }

    public String getRelatedPostIds() {
        return relatedPostIds;
    }

    public void setRelatedPostIds(String relatedPostIds) {
        this.relatedPostIds = relatedPostIds;
    }

    public String getRelatedMediaIds() {
        return relatedMediaIds;
    }

    public void setRelatedMediaIds(String relatedMediaIds) {
        this.relatedMediaIds = relatedMediaIds;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public void setRemovedAt(Instant removedAt) {
        this.removedAt = removedAt;
    }

    public Instant getUndoDeadlineAt() {
        return undoDeadlineAt;
    }

    public void setUndoDeadlineAt(Instant undoDeadlineAt) {
        this.undoDeadlineAt = undoDeadlineAt;
    }

    public Instant getRestoredAt() {
        return restoredAt;
    }

    public void setRestoredAt(Instant restoredAt) {
        this.restoredAt = restoredAt;
    }
}
