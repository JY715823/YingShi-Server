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

    // P1-3 隔离修复 S1: 照片流非分页路径需要排除 life domain 媒体, 避免照片流返回 life 媒体
    @Query("SELECT m FROM MediaEntity m WHERE m.libraryId = :libraryId AND m.deletedAt IS NULL AND (m.domain IS NULL OR m.domain != 'life')")
    List<MediaEntity> findByLibraryIdAndDeletedAtIsNullAndDomainNotLife(@Param("libraryId") String libraryId);

    List<MediaEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    List<MediaEntity> findByLibraryIdAndIdInAndDeletedAtIsNull(String libraryId, Collection<String> ids);

    @Query("SELECT m FROM MediaEntity m WHERE m.libraryId = :libraryId AND m.sourceFingerprint = :sourceFingerprint AND m.deletedAt IS NULL")
    Optional<MediaEntity> findFirstByLibraryIdAndSourceFingerprintAndDeletedAtIsNull(@Param("libraryId") String libraryId, @Param("sourceFingerprint") String sourceFingerprint);

    @Query("SELECT m FROM MediaEntity m WHERE m.libraryId = :libraryId AND m.sourceFingerprint IN :fingerprints AND m.deletedAt IS NULL")
    List<MediaEntity> findByLibraryIdAndSourceFingerprintInAndDeletedAtIsNull(@Param("libraryId") String libraryId, @Param("fingerprints") Collection<String> sourceFingerprints);

    // P1-3 隔离修复 S2: 照片 import-status 查询需要排除 life domain 媒体, 避免照片上传被错误跳过
    @Query("SELECT m FROM MediaEntity m WHERE m.libraryId = :libraryId AND m.sourceFingerprint IN :fingerprints AND m.deletedAt IS NULL AND (m.domain IS NULL OR m.domain != 'life')")
    List<MediaEntity> findByLibraryIdAndSourceFingerprintInAndDeletedAtIsNullAndDomainNotLife(@Param("libraryId") String libraryId, @Param("fingerprints") Collection<String> sourceFingerprints);

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

    // FR-5: 全局扫描最近更新的 media（不限 library），用于孤儿对象补偿。
    // 按 updatedAt DESC 排序，优先扫描最近上传的 media（最可能产生孤儿）。
    @Query("SELECT m FROM MediaEntity m ORDER BY m.updatedAt DESC")
    List<MediaEntity> findTopByOrderByUpdatedAtDesc(org.springframework.data.domain.Pageable pageable);

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

    // Round 8 照片流隔离: 排除 life domain 的 media, 避免今日痕迹上传触发照片流刷新
    @Query("SELECT MAX(m.updatedAt) FROM MediaEntity m WHERE m.libraryId = :libraryId AND (m.domain IS NULL OR m.domain != 'life')")
    Optional<Instant> findLatestUpdatedAtByLibraryIdAndDomainNotLife(@Param("libraryId") String libraryId);

    @Query("SELECT MAX(m.updatedAt) FROM MediaEntity m WHERE m.libraryId = :libraryId AND m.deletedAt IS NULL AND (m.domain IS NULL OR m.domain != 'life')")
    Optional<Instant> findLatestUpdatedAtByLibraryIdAndDeletedAtIsNullAndDomainNotLife(@Param("libraryId") String libraryId);

    // P1-1 改造: lifeConsoleVersion 直接追踪 life domain 的 media 更新（不再依赖 post 表）
    @Query("SELECT MAX(m.updatedAt) FROM MediaEntity m WHERE m.libraryId = :libraryId AND m.domain = 'life'")
    Optional<Instant> findLatestUpdatedAtByLibraryIdAndDomainLife(@Param("libraryId") String libraryId);

    @Query("SELECT MAX(m.updatedAt) FROM MediaEntity m WHERE m.libraryId = :libraryId AND m.deletedAt IS NULL AND m.domain = 'life'")
    Optional<Instant> findLatestUpdatedAtByLibraryIdAndDeletedAtIsNullAndDomainLife(@Param("libraryId") String libraryId);

    // P1-1 改造: 按 library + lifeCategory + displayTime 范围查询 life media（不再依赖 album/post 关联）
    // 用于 LifeConsoleService.buildMediaSlot / buildHistoryDays
    @Query("""
            SELECT m FROM MediaEntity m
            WHERE m.libraryId = :libraryId
              AND m.domain = 'life'
              AND m.lifeCategory = :lifeCategory
              AND m.deletedAt IS NULL
              AND m.displayTimeMillis >= :startMillis
              AND m.displayTimeMillis < :endMillis
            ORDER BY m.displayTimeMillis DESC
            """)
    List<MediaEntity> findLifeMediaByCategoryAndDisplayTimeRange(
            @Param("libraryId") String libraryId,
            @Param("lifeCategory") String lifeCategory,
            @Param("startMillis") Long startMillis,
            @Param("endMillis") Long endMillis
    );

    // P1-1 改造: 按 library + lifeCategory 查询所有未删除 life media（用于历史页填充缺失日期）
    @Query("""
            SELECT m FROM MediaEntity m
            WHERE m.libraryId = :libraryId
              AND m.domain = 'life'
              AND m.lifeCategory = :lifeCategory
              AND m.deletedAt IS NULL
            ORDER BY m.displayTimeMillis DESC
            """)
    List<MediaEntity> findLifeMediaByCategory(
            @Param("libraryId") String libraryId,
            @Param("lifeCategory") String lifeCategory
    );

    // P1-2 改造: 按 library + lifeCategory（非 null）查询所有 life trash media（含软删）
    @Query("""
            SELECT m FROM MediaEntity m
            WHERE m.libraryId = :libraryId
              AND m.domain = 'life'
              AND m.lifeCategory IS NOT NULL
              AND m.deletedAt IS NOT NULL
            ORDER BY m.deletedAt DESC
            """)
    List<MediaEntity> findLifeTrashMedia(@Param("libraryId") String libraryId);

    // P1-2 改造: 按 library + lifeCategory 查询 life trash media（含软删），分两类
    @Query("""
            SELECT m FROM MediaEntity m
            WHERE m.libraryId = :libraryId
              AND m.domain = 'life'
              AND m.lifeCategory = :lifeCategory
              AND m.deletedAt IS NOT NULL
            ORDER BY m.deletedAt DESC
            """)
    List<MediaEntity> findLifeTrashMediaByCategory(
            @Param("libraryId") String libraryId,
            @Param("lifeCategory") String lifeCategory
    );

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
