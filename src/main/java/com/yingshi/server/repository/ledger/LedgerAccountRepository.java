package com.yingshi.server.repository.ledger;

import com.yingshi.server.domain.ledger.LedgerAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccountEntity, String> {

    List<LedgerAccountEntity> findByLibraryId(String libraryId);

    List<LedgerAccountEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    List<LedgerAccountEntity> findByLibraryIdAndUpdatedAtAfter(String libraryId, Instant since);

    void deleteByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    Optional<LedgerAccountEntity> findByIdAndLibraryId(String id, String libraryId);
}
