package com.yingshi.server.repository.ledger;

import com.yingshi.server.domain.ledger.LedgerCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LedgerCategoryRepository extends JpaRepository<LedgerCategoryEntity, String> {

    List<LedgerCategoryEntity> findByLibraryId(String libraryId);

    List<LedgerCategoryEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    List<LedgerCategoryEntity> findByLibraryIdAndUpdatedAtAfter(String libraryId, Instant since);

    void deleteByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    Optional<LedgerCategoryEntity> findByIdAndLibraryId(String id, String libraryId);
}
