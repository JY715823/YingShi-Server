package com.yingshi.server.repository;

import com.yingshi.server.domain.AlbumEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlbumRepository extends JpaRepository<AlbumEntity, String> {

    List<AlbumEntity> findByLibraryIdOrderByTitleAsc(String libraryId);

    Optional<AlbumEntity> findByIdAndLibraryId(String id, String libraryId);

    Optional<AlbumEntity> findByLibraryIdAndSystemKey(String libraryId, String systemKey);

    List<AlbumEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    @Query("SELECT a FROM AlbumEntity a WHERE a.libraryId = :libraryId AND a.deletedAt IS NULL ORDER BY a.title ASC")
    List<AlbumEntity> findByLibraryIdAndDeletedAtIsNullOrderByTitleAsc(@Param("libraryId") String libraryId);

    @Query("SELECT a FROM AlbumEntity a WHERE a.libraryId = :libraryId AND a.id = :id AND a.deletedAt IS NULL")
    Optional<AlbumEntity> findByIdAndLibraryIdAndDeletedAtIsNull(@Param("id") String id, @Param("libraryId") String libraryId);

    @Query("SELECT a FROM AlbumEntity a WHERE a.libraryId = :libraryId AND a.systemKey = :systemKey AND a.deletedAt IS NULL")
    Optional<AlbumEntity> findByLibraryIdAndSystemKeyAndDeletedAtIsNull(@Param("libraryId") String libraryId, @Param("systemKey") String systemKey);

    @Query("SELECT a FROM AlbumEntity a WHERE a.libraryId = :libraryId AND a.id IN :ids AND a.deletedAt IS NULL")
    List<AlbumEntity> findByLibraryIdAndIdInAndDeletedAtIsNull(@Param("libraryId") String libraryId, @Param("ids") Collection<String> ids);

    @Query("SELECT a FROM AlbumEntity a WHERE a.libraryId = :libraryId AND a.systemKey = :systemKey AND a.deletedAt IS NULL")
    List<AlbumEntity> findAllByLibraryIdAndSystemKeyAndDeletedAtIsNull(@Param("libraryId") String libraryId, @Param("systemKey") String systemKey);

    @Query("SELECT a FROM AlbumEntity a WHERE a.libraryId = :libraryId AND a.deletedAt IS NULL AND a.systemKey IS NULL ORDER BY a.updatedAt DESC")
    Optional<AlbumEntity> findTopByLibraryIdAndDeletedAtIsNullAndSystemKeyIsNullOrderByUpdatedAtDesc(@Param("libraryId") String libraryId);

    @Query("SELECT a FROM AlbumEntity a WHERE a.libraryId = :libraryId AND a.systemKey IS NULL ORDER BY a.updatedAt DESC")
    Optional<AlbumEntity> findTopByLibraryIdAndSystemKeyIsNullOrderByUpdatedAtDesc(@Param("libraryId") String libraryId);

    @Query("SELECT MAX(a.updatedAt) FROM AlbumEntity a WHERE a.libraryId = :libraryId")
    Optional<Instant> findLatestUpdatedAtByLibraryId(@Param("libraryId") String libraryId);

    @Query("SELECT MAX(a.updatedAt) FROM AlbumEntity a WHERE a.libraryId = :libraryId AND a.deletedAt IS NULL")
    Optional<Instant> findLatestUpdatedAtByLibraryIdAndDeletedAtIsNull(@Param("libraryId") String libraryId);

    @Query("SELECT MAX(a.updatedAt) FROM AlbumEntity a WHERE a.libraryId = :libraryId AND a.deletedAt IS NULL AND a.systemKey IS NULL")
    Optional<Instant> findLatestUpdatedAtByLibraryIdAndDeletedAtIsNullAndSystemKeyIsNull(@Param("libraryId") String libraryId);

    @Query("SELECT MAX(a.updatedAt) FROM AlbumEntity a WHERE a.libraryId = :libraryId AND a.systemKey IS NULL")
    Optional<Instant> findLatestUpdatedAtByLibraryIdAndSystemKeyIsNull(@Param("libraryId") String libraryId);
}
