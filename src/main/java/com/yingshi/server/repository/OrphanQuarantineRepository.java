package com.yingshi.server.repository;

import com.yingshi.server.domain.OrphanQuarantineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OrphanQuarantineRepository extends JpaRepository<OrphanQuarantineEntity, Long> {

    /**
     * 查找隔离期已过且仍处于 QUARANTINED 状态的记录，可由 purgeQuarantined 真正删除。
     */
    @Query("SELECT q FROM OrphanQuarantineEntity q WHERE q.status = :status AND q.quarantineUntil < :cutoff ORDER BY q.detectedAt ASC")
    List<OrphanQuarantineEntity> findByStatusAndQuarantineUntilBefore(
            @Param("status") String status,
            @Param("cutoff") Instant cutoff
    );

    boolean existsByMediaIdAndObjectKey(String mediaId, String objectKey);
}
