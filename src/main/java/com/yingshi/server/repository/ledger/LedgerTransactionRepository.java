package com.yingshi.server.repository.ledger;

import com.yingshi.server.domain.ledger.LedgerTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransactionEntity, String> {

    List<LedgerTransactionEntity> findByLibraryId(String libraryId);

    List<LedgerTransactionEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    List<LedgerTransactionEntity> findByLibraryIdAndUpdatedAtAfter(String libraryId, Instant since);

    void deleteByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    Optional<LedgerTransactionEntity> findByIdAndLibraryId(String id, String libraryId);
}
