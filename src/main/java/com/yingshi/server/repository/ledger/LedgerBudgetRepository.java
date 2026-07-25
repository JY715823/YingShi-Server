package com.yingshi.server.repository.ledger;

import com.yingshi.server.domain.ledger.LedgerBudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // Round 3 FR-5: lifeConsoleVersion 改为读取关系表 MAX(updated_at)
    @Query("SELECT MAX(e.updatedAt) FROM LedgerBudgetEntity e WHERE e.libraryId = :libraryId")
    Optional<Instant> findLatestUpdatedAtByLibraryId(@Param("libraryId") String libraryId);

    // FR-3: soft delete — set deletedAtMillis + updatedAt. @PreUpdate is NOT triggered
    // by @Modifying bulk JPQL, so updatedAt must be set explicitly here.
    @Modifying
    @Query("UPDATE LedgerBudgetEntity e SET e.deletedAtMillis = :deletedAtMillis, e.updatedAt = :updatedAt " +
           "WHERE e.libraryId = :libraryId AND e.id IN :ids")
    void softDeleteByLibraryIdAndIdIn(
            @Param("libraryId") String libraryId,
            @Param("ids") Collection<String> ids,
            @Param("deletedAtMillis") Long deletedAtMillis,
            @Param("updatedAt") Instant updatedAt
    );
}
