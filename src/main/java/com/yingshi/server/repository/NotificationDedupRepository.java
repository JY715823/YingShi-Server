package com.yingshi.server.repository;

import com.yingshi.server.domain.NotificationDedupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface NotificationDedupRepository extends JpaRepository<NotificationDedupEntity, String> {

    /**
     * Delete dedup entries older than the given cutoff time.
     */
    @Modifying
    @Query("DELETE FROM NotificationDedupEntity e WHERE e.createdAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
