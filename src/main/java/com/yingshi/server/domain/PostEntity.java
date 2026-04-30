package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "posts")
public class PostEntity extends SpaceScopedEntity {

    @Id
    private String id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 1000)
    private String summary;

    @Column(nullable = false)
    private Long displayTimeMillis;

    @Column(length = 120)
    private String contributorLabel;

    @Column
    private String coverMediaId;

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

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Long getDisplayTimeMillis() {
        return displayTimeMillis;
    }

    public void setDisplayTimeMillis(Long displayTimeMillis) {
        this.displayTimeMillis = displayTimeMillis;
    }

    public String getContributorLabel() {
        return contributorLabel;
    }

    public void setContributorLabel(String contributorLabel) {
        this.contributorLabel = contributorLabel;
    }

    public String getCoverMediaId() {
        return coverMediaId;
    }

    public void setCoverMediaId(String coverMediaId) {
        this.coverMediaId = coverMediaId;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
