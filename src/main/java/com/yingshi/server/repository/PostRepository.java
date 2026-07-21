package com.yingshi.server.repository;

import com.yingshi.server.domain.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<PostEntity, String> {

    Optional<PostEntity> findByIdAndLibraryId(String id, String libraryId);

    Optional<PostEntity> findByIdAndLibraryIdAndDeletedAtIsNull(String id, String libraryId);

    Optional<PostEntity> findByLibraryIdAndAlbumIdAndSystemKeyAndDeletedAtIsNull(String libraryId, String albumId, String systemKey);

    @Query("SELECT p FROM PostEntity p WHERE p.libraryId = :libraryId AND p.albumId = :albumId AND p.systemKey = :systemKey AND p.domain = :domain AND p.deletedAt IS NULL")
    Optional<PostEntity> findByLibraryIdAndAlbumIdAndSystemKeyAndDomainAndDeletedAtIsNull(@Param("libraryId") String libraryId, @Param("albumId") String albumId, @Param("systemKey") String systemKey, @Param("domain") String domain);

    // Round 8 Bug 修复: 查找软删除的月度 small_album 用于复活 (uk_small_albums_library_album_system_key 不含 deleted_at)
    @Query("SELECT p FROM PostEntity p WHERE p.libraryId = :libraryId AND p.albumId = :albumId AND p.systemKey = :systemKey AND p.domain = :domain ORDER BY p.deletedAt NULLS FIRST")
    Optional<PostEntity> findByLibraryIdAndAlbumIdAndSystemKeyAndDomain(@Param("libraryId") String libraryId, @Param("albumId") String albumId, @Param("systemKey") String systemKey, @Param("domain") String domain);

    List<PostEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    List<PostEntity> findByLibraryIdAndIdInAndDeletedAtIsNull(String libraryId, Collection<String> ids);

    @Query("SELECT p FROM PostEntity p WHERE p.libraryId = :libraryId AND p.deletedAt IS NULL ORDER BY p.displayTimeMillis DESC, p.updatedAt DESC")
    List<PostEntity> findByLibraryIdAndDeletedAtIsNullOrderByDisplayTimeMillisDescUpdatedAtDesc(@Param("libraryId") String libraryId);

    @Query("SELECT p FROM PostEntity p WHERE p.libraryId = :libraryId AND p.deletedAt IS NULL ORDER BY p.updatedAt DESC")
    List<PostEntity> findByLibraryIdAndDeletedAtIsNullOrderByUpdatedAtDesc(@Param("libraryId") String libraryId);

    @Query("SELECT p FROM PostEntity p WHERE p.libraryId = :libraryId AND p.deletedAt IS NULL AND p.systemKey IS NULL ORDER BY p.updatedAt DESC")
    Optional<PostEntity> findFirstByLibraryIdAndDeletedAtIsNullAndSystemKeyIsNullOrderByUpdatedAtDesc(@Param("libraryId") String libraryId);

    @Query("SELECT p FROM PostEntity p WHERE p.libraryId = :libraryId AND p.systemKey IS NULL ORDER BY p.updatedAt DESC")
    Optional<PostEntity> findFirstByLibraryIdAndSystemKeyIsNullOrderByUpdatedAtDesc(@Param("libraryId") String libraryId);

    @Query("SELECT p FROM PostEntity p WHERE p.libraryId = :libraryId AND p.deletedAt IS NULL AND p.systemKey IS NOT NULL ORDER BY p.updatedAt DESC")
    Optional<PostEntity> findFirstByLibraryIdAndDeletedAtIsNullAndSystemKeyIsNotNullOrderByUpdatedAtDesc(@Param("libraryId") String libraryId);

    @Query("SELECT p FROM PostEntity p WHERE p.libraryId = :libraryId AND p.albumId = :albumId AND p.deletedAt IS NULL ORDER BY p.displayTimeMillis DESC, p.updatedAt DESC")
    List<PostEntity> findByLibraryIdAndAlbumIdAndDeletedAtIsNullOrderByDisplayTimeMillisDescUpdatedAtDesc(
            @Param("libraryId") String libraryId,
            @Param("albumId") String albumId
    );

    @Query("SELECT p FROM PostEntity p WHERE p.libraryId = :libraryId AND p.albumId = :albumId AND p.domain = :domain AND p.deletedAt IS NULL ORDER BY p.displayTimeMillis DESC, p.updatedAt DESC")
    List<PostEntity> findByLibraryIdAndAlbumIdAndDomainAndDeletedAtIsNullOrderByDisplayTimeMillisDescUpdatedAtDesc(
            @Param("libraryId") String libraryId,
            @Param("albumId") String albumId,
            @Param("domain") String domain
    );

    @Query("""
            SELECT p.albumId, COUNT(p) FROM PostEntity p
            WHERE p.libraryId = :libraryId AND p.deletedAt IS NULL
            GROUP BY p.albumId
            """)
    List<Object[]> countActivePostsGroupByAlbumId(@Param("libraryId") String libraryId);

    @Query("SELECT COUNT(p) FROM PostEntity p WHERE p.libraryId = :libraryId AND p.albumId = :albumId AND p.deletedAt IS NULL")
    long countByLibraryIdAndAlbumIdAndDeletedAtIsNull(@Param("libraryId") String libraryId, @Param("albumId") String albumId);

    @Query("SELECT MAX(p.updatedAt) FROM PostEntity p WHERE p.libraryId = :libraryId")
    Optional<Instant> findLatestUpdatedAtByLibraryId(@Param("libraryId") String libraryId);

    @Query("SELECT MAX(p.updatedAt) FROM PostEntity p WHERE p.libraryId = :libraryId AND p.deletedAt IS NULL")
    Optional<Instant> findLatestUpdatedAtByLibraryIdAndDeletedAtIsNull(@Param("libraryId") String libraryId);

    @Query("SELECT MAX(p.updatedAt) FROM PostEntity p WHERE p.libraryId = :libraryId AND p.deletedAt IS NULL AND p.systemKey IS NULL")
    Optional<Instant> findLatestUpdatedAtByLibraryIdAndDeletedAtIsNullAndSystemKeyIsNull(@Param("libraryId") String libraryId);

    @Query("SELECT MAX(p.updatedAt) FROM PostEntity p WHERE p.libraryId = :libraryId AND p.systemKey IS NULL")
    Optional<Instant> findLatestUpdatedAtByLibraryIdAndSystemKeyIsNull(@Param("libraryId") String libraryId);

    @Query("SELECT MAX(p.updatedAt) FROM PostEntity p WHERE p.libraryId = :libraryId AND p.deletedAt IS NULL AND p.systemKey IS NOT NULL")
    Optional<Instant> findLatestUpdatedAtByLibraryIdAndDeletedAtIsNullAndSystemKeyIsNotNull(@Param("libraryId") String libraryId);

    @Query("SELECT MAX(p.updatedAt) FROM PostEntity p WHERE p.libraryId = :libraryId AND p.domain = :domain AND p.deletedAt IS NULL AND p.systemKey IS NOT NULL")
    Optional<Instant> findLatestUpdatedAtByLibraryIdAndDomainAndDeletedAtIsNullAndSystemKeyIsNotNull(@Param("libraryId") String libraryId, @Param("domain") String domain);
}
