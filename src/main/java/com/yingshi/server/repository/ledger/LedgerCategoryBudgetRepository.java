package com.yingshi.server.repository.ledger;

import com.yingshi.server.domain.ledger.LedgerCategoryBudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LedgerCategoryBudgetRepository extends JpaRepository<LedgerCategoryBudgetEntity, String> {

    List<LedgerCategoryBudgetEntity> findByLibraryId(String libraryId);

    List<LedgerCategoryBudgetEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    List<LedgerCategoryBudgetEntity> findByLibraryIdAndUpdatedAtAfter(String libraryId, Instant since);

    void deleteByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    Optional<LedgerCategoryBudgetEntity> findByIdAndLibraryId(String id, String libraryId);
}
