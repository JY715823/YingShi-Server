package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "post_albums",
        uniqueConstraints = @UniqueConstraint(name = "uk_post_album_post_album", columnNames = {"spaceId", "postId", "albumId"})
)
public class PostAlbumEntity extends SpaceScopedEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String postId;

    @Column(nullable = false)
    private String albumId;

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

    public String getAlbumId() {
        return albumId;
    }

    public void setAlbumId(String albumId) {
        this.albumId = albumId;
    }
}
