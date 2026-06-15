package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "albums")
public class AlbumEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 255)
    private String subtitle;

    @Column
    private String coverMediaId;

    @Column(length = 80)
    private String systemKey;

    @Column(nullable = false)
    private Boolean includeInPhotoFeed = true;

    @Column
    private Instant deletedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getCoverMediaId() {
        return coverMediaId;
    }

    public void setCoverMediaId(String coverMediaId) {
        this.coverMediaId = coverMediaId;
    }

    public String getSystemKey() {
        return systemKey;
    }

    public void setSystemKey(String systemKey) {
        this.systemKey = systemKey;
    }

    public Boolean getIncludeInPhotoFeed() {
        return includeInPhotoFeed;
    }

    public void setIncludeInPhotoFeed(Boolean includeInPhotoFeed) {
        this.includeInPhotoFeed = includeInPhotoFeed;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
