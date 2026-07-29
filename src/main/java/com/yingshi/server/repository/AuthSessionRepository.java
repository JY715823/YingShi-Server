package com.yingshi.server.repository;

import com.yingshi.server.domain.AuthSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * PESSIMISTIC_WRITE 行锁查询 - 用于 refresh token rotation。
     * 通过 id + userId + libraryId 精确定位会话并加悲观行锁，
     * 保证 rotation 期间的并发安全。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AuthSessionEntity s WHERE s.id = :id AND s.userId = :userId AND s.libraryId = :libraryId AND s.revokedAt IS NULL")
    Optional<AuthSessionEntity> findByIdAndUserIdAndLibraryIdWithLock(
            @Param("id") String id,
            @Param("userId") String userId,
            @Param("libraryId") String libraryId
    );

    /**
     * 批量撤销会话族 - 用于 refresh token 重放检测。
     * 通过版本号自增保证并发安全。
     */
    @Modifying
    @Query("UPDATE AuthSessionEntity s SET s.revokedAt = :revokedAt, s.version = s.version + 1 WHERE s.userId = :userId AND s.libraryId = :libraryId AND s.revokedAt IS NULL")
    int revokeAllByUserAndLibrary(
            @Param("userId") String userId,
            @Param("libraryId") String libraryId,
            @Param("revokedAt") Instant revokedAt
    );
}
