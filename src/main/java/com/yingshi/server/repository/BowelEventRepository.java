package com.yingshi.server.repository;

import com.yingshi.server.domain.BowelEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BowelEventRepository extends JpaRepository<BowelEventEntity, String> {

    List<BowelEventEntity> findByLibraryIdAndOccurredAtMillisGreaterThanEqualAndOccurredAtMillisLessThanOrderByOccurredAtMillisAsc(
            String libraryId,
            Long startMillis,
            Long endMillis
    );

    Optional<BowelEventEntity> findFirstByLibraryIdAndUserIdAndOccurredAtMillisGreaterThanEqualAndOccurredAtMillisLessThanOrderByOccurredAtMillisDesc(
            String libraryId,
            String userId,
            Long startMillis,
            Long endMillis
    );

    Optional<BowelEventEntity> findFirstByLibraryIdOrderByUpdatedAtDesc(String libraryId);

    List<BowelEventEntity> findTop50ByLibraryIdOrderByOccurredAtMillisDesc(String libraryId);
}
