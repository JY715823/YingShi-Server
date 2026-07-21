package com.yingshi.server.repository;

import com.yingshi.server.domain.BowelEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BowelEventRepository extends JpaRepository<BowelEventEntity, String> {

    @Query("SELECT b FROM BowelEventEntity b WHERE b.libraryId = :libraryId AND b.occurredAtMillis >= :startMillis AND b.occurredAtMillis < :endMillis AND b.deletedAt IS NULL ORDER BY b.occurredAtMillis ASC")
    List<BowelEventEntity> findByLibraryIdAndOccurredAtMillisGreaterThanEqualAndOccurredAtMillisLessThanOrderByOccurredAtMillisAsc(
            @Param("libraryId") String libraryId,
            @Param("startMillis") Long startMillis,
            @Param("endMillis") Long endMillis
    );

    @Query("SELECT b FROM BowelEventEntity b WHERE b.libraryId = :libraryId AND b.userId = :userId AND b.occurredAtMillis >= :startMillis AND b.occurredAtMillis < :endMillis AND b.deletedAt IS NULL ORDER BY b.occurredAtMillis DESC")
    List<BowelEventEntity> findLatestByLibraryIdAndUserIdAndOccurredAtMillisGreaterThanEqualAndOccurredAtMillisLessThan(
            @Param("libraryId") String libraryId,
            @Param("userId") String userId,
            @Param("startMillis") Long startMillis,
            @Param("endMillis") Long endMillis
    );

    @Query("SELECT MAX(b.updatedAt) FROM BowelEventEntity b WHERE b.libraryId = :libraryId")
    Optional<Instant> findLatestUpdatedAtByLibraryId(@Param("libraryId") String libraryId);

    List<BowelEventEntity> findTop50ByLibraryIdAndDeletedAtIsNullOrderByOccurredAtMillisDesc(String libraryId);

    // Round 7: 按 id + libraryId 查询单条大便事件（用于 update location 接口）
    Optional<BowelEventEntity> findByIdAndLibraryId(String id, String libraryId);
}
