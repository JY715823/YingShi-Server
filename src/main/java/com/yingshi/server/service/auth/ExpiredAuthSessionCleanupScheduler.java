package com.yingshi.server.service.auth;

import com.yingshi.server.repository.AuthSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Periodically cleans up expired and revoked auth sessions to prevent unbounded
 * growth of the auth_sessions table.
 *
 * <p>Two cleanup strategies:
 * <ul>
 *   <li>Revoked sessions older than 30 days are physically deleted</li>
 *   <li>Expired sessions (refreshExpireAt in the past) older than 30 days are physically deleted</li>
 * </ul>
 *
 * <p>Runs every hour with a 10-minute initial delay.
 */
@Component
public class ExpiredAuthSessionCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiredAuthSessionCleanupScheduler.class);
    private static final long RETENTION_DAYS = 30;

    private final AuthSessionRepository authSessionRepository;

    public ExpiredAuthSessionCleanupScheduler(AuthSessionRepository authSessionRepository) {
        this.authSessionRepository = authSessionRepository;
    }

    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 600_000L)
    @Transactional
    public void cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);

        int revokedDeleted = authSessionRepository.deleteRevokedBefore(cutoff);
        int expiredDeleted = authSessionRepository.deleteExpiredBefore(cutoff);
        int totalDeleted = revokedDeleted + expiredDeleted;

        if (totalDeleted > 0) {
            log.info("Auth session cleanup: deleted {} revoked + {} expired = {} total sessions (cutoff: {})",
                    revokedDeleted, expiredDeleted, totalDeleted, cutoff);
        }
    }
}
