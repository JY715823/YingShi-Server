package com.yingshi.server.repository.ledger;

import com.yingshi.server.domain.ledger.LedgerRecurringOccurrenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LedgerRecurringOccurrenceRepository extends JpaRepository<LedgerRecurringOccurrenceEntity, String> {

    List<LedgerRecurringOccurrenceEntity> findByLibraryId(String libraryId);

    List<LedgerRecurringOccurrenceEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    List<LedgerRecurringOccurrenceEntity> findByLibraryIdAndUpdatedAtAfter(String libraryId, Instant since);

    void deleteByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    Optional<LedgerRecurringOccurrenceEntity> findByIdAndLibraryId(String id, String libraryId);
}
