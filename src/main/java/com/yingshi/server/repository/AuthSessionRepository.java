package com.yingshi.server.repository;

import com.yingshi.server.domain.AuthSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, String> {

    Optional<AuthSessionEntity> findByIdAndUserIdAndLibraryId(String id, String userId, String libraryId);

    /**
     * Delete sessions that have been revoked more than 30 days ago.
     */
    @Modifying
    @Query("DELETE FROM AuthSessionEntity s WHERE s.revokedAt IS NOT NULL AND s.revokedAt < :cutoff")
    int deleteRevokedBefore(@Param("cutoff") Instant cutoff);

    /**
     * Delete sessions whose refresh token has expired more than 30 days ago.
     */
    @Modifying
    @Query("DELETE FROM AuthSessionEntity s WHERE s.refreshExpireAt < :cutoff AND s.revokedAt IS NULL")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
