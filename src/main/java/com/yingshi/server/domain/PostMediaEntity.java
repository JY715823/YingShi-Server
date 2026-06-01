package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "small_album_media",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_small_album_media_small_album_media", columnNames = {"library_id", "small_album_id", "media_id"}),
                @UniqueConstraint(name = "uk_small_album_media_small_album_sort", columnNames = {"library_id", "small_album_id", "sort_order"})
        }
)
public class PostMediaEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(name = "small_album_id", nullable = false)
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
