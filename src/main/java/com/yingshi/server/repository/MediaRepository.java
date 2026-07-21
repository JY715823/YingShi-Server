package com.yingshi.server.repository;

import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MediaRepository extends JpaRepository<MediaEntity, String> {

    Optional<MediaEntity> findByIdAndLibraryId(String id, String libraryId);

    Optional<MediaEntity> findByIdAndLibraryIdAndDeletedAtIsNull(String id, String libraryId);

    List<MediaEntity> findByLibraryId(String libraryId);

    List<MediaEntity> findByLibraryIdAndDeletedAtIsNull(String libraryId);

    List<MediaEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    List<MediaEntity> findByLibraryIdAndIdInAndDeletedAtIsNull(String libraryId, Collection<String> ids);

    @Query("SELECT m FROM MediaEntity m WHERE m.libraryId = :libraryId AND m.sourceFingerprint = :sourceFingerprint AND m.deletedAt IS NULL")
    Optional<MediaEntity> findFirstByLibraryIdAndSourceFingerprintAndDeletedAtIsNull(@Param("libraryId") String libraryId, @Param("sourceFingerprint") String sourceFingerprint);

    @Query("SELECT m FROM MediaEntity m WHERE m.libraryId = :libraryId AND m.sourceFingerprint IN :fingerprints AND m.deletedAt IS NULL")
    List<MediaEntity> findByLibraryIdAndSourceFingerprintInAndDeletedAtIsNull(@Param("libraryId") String libraryId, @Param("fingerprints") Collection<String> sourceFingerprints);

    @Query("""
            SELECT m FROM MediaEntity m
            WHERE m.mediaType = :mediaType AND m.deletedAt IS NULL
            ORDER BY m.updatedAt DESC
            """)
    List<MediaEntity> findTop200RecentByMediaType(@Param("mediaType") MediaType mediaType,
                                                  org.springframework.data.domain.Pageable pageable);

    @Query("SELECT m FROM MediaEntity m WHERE m.libraryId = :libraryId AND m.deletedAt IS NULL ORDER BY m.updatedAt DESC")
    List<MediaEntity> findTopByLibraryIdAndDeletedAtIsNullOrderByUpdatedAtDesc(@Param("libraryId") String libraryId,
                                                                                  org.springframework.data.domain.Pageable pageable);

    @Query("SELECT m FROM MediaEntity m WHERE m.libraryId = :libraryId ORDER BY m.updatedAt DESC")
    List<MediaEntity> findTopByLibraryIdOrderByUpdatedAtDesc(@Param("libraryId") String libraryId,
                                                                 org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT m FROM MediaEntity m
            WHERE m.libraryId = :libraryId
              AND m.mediaType = :mediaType
              AND m.mimeType = :mimeType
              AND m.sizeBytes = :sizeBytes
              AND m.displayTimeMillis = :displayTimeMillis
              AND m.width = :width
              AND m.height = :height
              AND m.durationMillis IS NULL
              AND m.deletedAt IS NULL
            """)
    Optional<MediaEntity> findDuplicateWithoutDuration(
            @Param("libraryId") String libraryId,
            @Param("mediaType") MediaType mediaType,
            @Param("mimeType") String mimeType,
            @Param("sizeBytes") Long sizeBytes,
            @Param("displayTimeMillis") Long displayTimeMillis,
            @Param("width") Integer width,
            @Param("height") Integer height
    );

    @Query("""
            SELECT m FROM MediaEntity m
            WHERE m.libraryId = :libraryId
              AND m.mediaType = :mediaType
              AND m.mimeType = :mimeType
              AND m.sizeBytes = :sizeBytes
              AND m.displayTimeMillis = :displayTimeMillis
              AND m.width = :width
              AND m.height = :height
              AND m.durationMillis = :durationMillis
              AND m.deletedAt IS NULL
            """)
    Optional<MediaEntity> findDuplicateWithDuration(
            @Param("libraryId") String libraryId,
            @Param("mediaType") MediaType mediaType,
            @Param("mimeType") String mimeType,
            @Param("sizeBytes") Long sizeBytes,
            @Param("displayTimeMillis") Long displayTimeMillis,
            @Param("width") Integer width,
            @Param("height") Integer height,
            @Param("durationMillis") Long durationMillis
    );

    @Query("SELECT MAX(m.updatedAt) FROM MediaEntity m WHERE m.libraryId = :libraryId")
    Optional<Instant> findLatestUpdatedAtByLibraryId(@Param("libraryId") String libraryId);

    @Query("SELECT MAX(m.updatedAt) FROM MediaEntity m WHERE m.libraryId = :libraryId AND m.deletedAt IS NULL")
    Optional<Instant> findLatestUpdatedAtByLibraryIdAndDeletedAtIsNull(@Param("libraryId") String libraryId);

    // Round 8: 查询某个用户在指定时间范围内的所有 media (不限 domain), 用于 life 模块 partner 回退展示
    @Query("""
            SELECT m FROM MediaEntity m
            WHERE m.libraryId = :libraryId
              AND m.recordOwnerUserId = :userId
              AND m.deletedAt IS NULL
              AND m.displayTimeMillis >= :startMillis
              AND m.displayTimeMillis < :endMillis
            ORDER BY m.displayTimeMillis DESC
            """)
    List<MediaEntity> findByLibraryIdAndUserIdAndDisplayTimeRange(
            @Param("libraryId") String libraryId,
            @Param("userId") String userId,
            @Param("startMillis") Long startMillis,
            @Param("endMillis") Long endMillis
    );

    @Query("""
            SELECT m FROM MediaEntity m
            WHERE m.libraryId = :libraryId
              AND m.domain = 'photo'
              AND m.deletedAt IS NULL
              AND (:cursorDisplayTime IS NULL
                   OR m.displayTimeMillis < :cursorDisplayTime
                   OR (m.displayTimeMillis = :cursorDisplayTime AND m.id > :cursorMediaId))
            ORDER BY m.displayTimeMillis DESC, m.id ASC
            """)
    List<MediaEntity> findFeedPage(@Param("libraryId") String libraryId,
                                   @Param("cursorDisplayTime") Long cursorDisplayTime,
                                   @Param("cursorMediaId") String cursorMediaId,
                                   org.springframework.data.domain.Pageable pageable);
}
