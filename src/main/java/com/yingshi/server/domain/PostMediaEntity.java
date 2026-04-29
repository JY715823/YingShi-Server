package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "post_media",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_post_media_post_media", columnNames = {"spaceId", "postId", "mediaId"}),
                @UniqueConstraint(name = "uk_post_media_post_sort", columnNames = {"spaceId", "postId", "sortOrder"})
        }
)
public class PostMediaEntity extends SpaceScopedEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String postId;

    @Column(nullable = false)
    private String mediaId;

    @Column(nullable = false)
    private Integer sortOrder;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
