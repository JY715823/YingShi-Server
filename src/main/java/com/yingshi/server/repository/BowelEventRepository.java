package com.yingshi.server.repository;

import com.yingshi.server.domain.BowelEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BowelEventRepository extends JpaRepository<BowelEventEntity, String> {

    @Query("SELECT b FROM BowelEventEntity b WHERE b.libraryId = :libraryId AND b.occurredAtMillis >= :startMillis AND b.occurredAtMillis < :endMillis ORDER BY b.occurredAtMillis ASC")
    List<BowelEventEntity> findByLibraryIdAndOccurredAtMillisGreaterThanEqualAndOccurredAtMillisLessThanOrderByOccurredAtMillisAsc(
            @Param("libraryId") String libraryId,
            @Param("startMillis") Long startMillis,
            @Param("endMillis") Long endMillis
    );

    @Query("SELECT b FROM BowelEventEntity b WHERE b.libraryId = :libraryId AND b.userId = :userId AND b.occurredAtMillis >= :startMillis AND b.occurredAtMillis < :endMillis ORDER BY b.occurredAtMillis DESC")
    Optional<BowelEventEntity> findFirstByLibraryIdAndUserIdAndOccurredAtMillisGreaterThanEqualAndOccurredAtMillisLessThanOrderByOccurredAtMillisDesc(
            @Param("libraryId") String libraryId,
            @Param("userId") String userId,
            @Param("startMillis") Long startMillis,
            @Param("endMillis") Long endMillis
    );

    @Query("SELECT MAX(b.updatedAt) FROM BowelEventEntity b WHERE b.libraryId = :libraryId")
    Optional<Instant> findLatestUpdatedAtByLibraryId(@Param("libraryId") String libraryId);

    List<BowelEventEntity> findTop50ByLibraryIdOrderByOccurredAtMillisDesc(String libraryId);
}
