package com.yingshi.server.service.push;

import com.yingshi.server.domain.PushDeviceTokenEntity;
import com.yingshi.server.repository.PushDeviceTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Periodically cleans up stale push device tokens.
 *
 * - Tokens not seen for 60+ days are soft-disabled (setEnabled false).
 * - Tokens not seen for 180+ days are physically deleted.
 *
 * This complements the passive disabling that happens when FCM returns
 * UNREGISTERED/INVALID_ARGUMENT. Without this scheduler, tokens for
 * uninstalled apps or factory-reset devices would linger forever.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.push.token-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class StalePushTokenCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(StalePushTokenCleanupScheduler.class);

    private static final long STALE_THRESHOLD_MILLIS = 60L * 24 * 60 * 60 * 1000;   // 60 days
    private static final long PURGE_THRESHOLD_MILLIS = 180L * 24 * 60 * 60 * 1000;  // 180 days

    private final PushDeviceTokenRepository pushDeviceTokenRepository;

    public StalePushTokenCleanupScheduler(PushDeviceTokenRepository pushDeviceTokenRepository) {
        this.pushDeviceTokenRepository = pushDeviceTokenRepository;
    }

    @Scheduled(
            fixedDelayString = "${app.push.token-cleanup.fixed-delay-millis:3600000}",
            initialDelayString = "${app.push.token-cleanup.initial-delay-millis:300000}"
    )
    @Transactional
    public void cleanupStaleTokens() {
        long now = Instant.now().toEpochMilli();
        long staleBoundary = now - STALE_THRESHOLD_MILLIS;
        long purgeBoundary = now - PURGE_THRESHOLD_MILLIS;

        // 1. Soft-disable tokens inactive for 60-180 days
        List<PushDeviceTokenEntity> stale = pushDeviceTokenRepository
                .findByEnabledTrueAndLastSeenAtMillisBefore(staleBoundary);
        for (PushDeviceTokenEntity token : stale) {
            token.setEnabled(false);
            pushDeviceTokenRepository.save(token);
            logger.info("Disabled stale push token: tokenId={}, userId={}, lastSeenAtMillis={}",
                    token.getId(), token.getUserId(), token.getLastSeenAtMillis());
        }

        // 2. Physically delete tokens inactive for 180+ days
        List<PushDeviceTokenEntity> dead = pushDeviceTokenRepository
                .findByLastSeenAtMillisBefore(purgeBoundary);
        for (PushDeviceTokenEntity token : dead) {
            pushDeviceTokenRepository.delete(token);
            logger.info("Purged dead push token: tokenId={}, userId={}, lastSeenAtMillis={}",
                    token.getId(), token.getUserId(), token.getLastSeenAtMillis());
        }

        if (!stale.isEmpty() || !dead.isEmpty()) {
            logger.info("Push token cleanup: disabled {} stale, purged {} dead", stale.size(), dead.size());
        }
    }
}
