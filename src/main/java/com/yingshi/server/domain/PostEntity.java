package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "small_albums")
public class PostEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 1000)
    private String summary;

    @Column(nullable = false)
    private String albumId;

    @Column(nullable = false)
    private Long displayTimeMillis;

    @Column
    private Long eventStartedAtMillis;

    @Column
    private Long eventEndedAtMillis;

    @Column(nullable = false, length = 20)
    private String displayTimeSource;

    @Column(length = 120)
    private String contributorLabel;

    @Column
    private String coverMediaId;

    @Column(name = "creator_user_id", length = 255)
    private String creatorUserId;

    @Column(name = "participant_user_ids", length = 2000)
    private String participantUserIds;

    @Column
    private Instant deletedAt;

    @Column(length = 80)
    private String systemKey;

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

    public String getAlbumId() {
        return albumId;
    }

    public void setAlbumId(String albumId) {
        this.albumId = albumId;
    }

    public Long getDisplayTimeMillis() {
        return displayTimeMillis;
    }

    public void setDisplayTimeMillis(Long displayTimeMillis) {
        this.displayTimeMillis = displayTimeMillis;
    }

    public Long getEventStartedAtMillis() {
        return eventStartedAtMillis;
    }

    public void setEventStartedAtMillis(Long eventStartedAtMillis) {
        this.eventStartedAtMillis = eventStartedAtMillis;
    }

    public Long getEventEndedAtMillis() {
        return eventEndedAtMillis;
    }

    public void setEventEndedAtMillis(Long eventEndedAtMillis) {
        this.eventEndedAtMillis = eventEndedAtMillis;
    }

    public String getDisplayTimeSource() {
        return displayTimeSource;
    }

    public void setDisplayTimeSource(String displayTimeSource) {
        this.displayTimeSource = displayTimeSource;
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

    public String getCreatorUserId() {
        return creatorUserId;
    }

    public void setCreatorUserId(String creatorUserId) {
        this.creatorUserId = creatorUserId;
    }

    public String getParticipantUserIds() {
        return participantUserIds;
    }

    public void setParticipantUserIds(String participantUserIds) {
        this.participantUserIds = participantUserIds;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getSystemKey() {
        return systemKey;
    }

    public void setSystemKey(String systemKey) {
        this.systemKey = systemKey;
    }
}
