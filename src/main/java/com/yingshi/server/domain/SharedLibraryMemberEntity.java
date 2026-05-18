package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "shared_library_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_shared_library_member_library_user", columnNames = {"libraryId", "userId"})
)
public class SharedLibraryMemberEntity extends BaseEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String libraryId;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SharedLibraryRole role;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLibraryId() {
        return libraryId;
    }

    public void setLibraryId(String libraryId) {
        this.libraryId = libraryId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public SharedLibraryRole getRole() {
        return role;
    }

    public void setRole(SharedLibraryRole role) {
        this.role = role;
    }
}
