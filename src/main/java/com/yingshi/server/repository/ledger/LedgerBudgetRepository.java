package com.yingshi.server.repository.ledger;

import com.yingshi.server.domain.ledger.LedgerBudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LedgerBudgetRepository extends JpaRepository<LedgerBudgetEntity, String> {

    List<LedgerBudgetEntity> findByLibraryId(String libraryId);

    List<LedgerBudgetEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    List<LedgerBudgetEntity> findByLibraryIdAndUpdatedAtAfter(String libraryId, Instant since);

    void deleteByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    Optional<LedgerBudgetEntity> findByIdAndLibraryId(String id, String libraryId);
}
