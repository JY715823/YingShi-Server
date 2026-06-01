package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "comments")
public class CommentEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommentTargetType targetType;

    @Column(name = "small_album_id")
    private String postId;

    @Column
    private String mediaId;

    @Column(length = 2000)
    private String content;

    @Column
    private String lastEditedByUserId;

    @Column
    private String deletedByUserId;

    @Column
    private Instant deletedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public CommentTargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(CommentTargetType targetType) {
        this.targetType = targetType;
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public String getMediaId() {
        return mediaId;
    }

    public void setMediaId(String mediaId) {
        this.mediaId = mediaId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLastEditedByUserId() {
        return lastEditedByUserId;
    }

    public void setLastEditedByUserId(String lastEditedByUserId) {
        this.lastEditedByUserId = lastEditedByUserId;
    }

    public String getDeletedByUserId() {
        return deletedByUserId;
    }

    public void setDeletedByUserId(String deletedByUserId) {
        this.deletedByUserId = deletedByUserId;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
