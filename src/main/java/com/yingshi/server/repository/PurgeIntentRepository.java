package com.yingshi.server.repository;

import com.yingshi.server.domain.PurgeIntentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * R3-TRASH-002: Repository for purge_intents outbox table.
 */
public interface PurgeIntentRepository extends JpaRepository<PurgeIntentEntity, String> {

    /**
     * Processor scan: find pending or failed intents due for (re)try.
     * PENDING intents with next_retry_at NULL or in the past are picked up immediately;
     * FAILED intents are picked up after their next_retry_at cutoff.
     */
    @Query("SELECT pi FROM PurgeIntentEntity pi WHERE pi.state IN :states AND (pi.nextRetryAt IS NULL OR pi.nextRetryAt <= :cutoff) ORDER BY pi.createdAt ASC")
    List<PurgeIntentEntity> findByStateInAndNextRetryAtBefore(
            @Param("states") List<String> states,
            @Param("cutoff") Instant cutoff
    );

    List<PurgeIntentEntity> findByTrashItemId(String trashItemId);

    @Override
    Optional<PurgeIntentEntity> findById(String id);
}
