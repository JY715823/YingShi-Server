package com.yingshi.server.repository.ledger;

import com.yingshi.server.domain.ledger.LedgerRecurringRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LedgerRecurringRuleRepository extends JpaRepository<LedgerRecurringRuleEntity, String> {

    List<LedgerRecurringRuleEntity> findByLibraryId(String libraryId);

    List<LedgerRecurringRuleEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    List<LedgerRecurringRuleEntity> findByLibraryIdAndUpdatedAtAfter(String libraryId, Instant since);

    void deleteByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    Optional<LedgerRecurringRuleEntity> findByIdAndLibraryId(String id, String libraryId);
}
