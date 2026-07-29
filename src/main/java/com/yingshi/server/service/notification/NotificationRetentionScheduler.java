package com.yingshi.server.service.notification;

import com.yingshi.server.repository.NotificationEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * R2-A-1: Notification event retention scheduler.
 *
 * <p>Runs daily at 03:00 to delete notification_events rows whose
 * {@code expires_at} has passed. This keeps the table from growing
 * unbounded — events are retained for 30 days (set at insert time via
 * {@link com.yingshi.server.domain.notification.NotificationEventEntity})
 * to support SSE replay after server restart or long client disconnects.
 */
@Component
public class NotificationRetentionScheduler {
    private static final Logger log = LoggerFactory.getLogger(NotificationRetentionScheduler.class);

    private final NotificationEventRepository notificationEventRepository;

    public NotificationRetentionScheduler(NotificationEventRepository notificationEventRepository) {
        this.notificationEventRepository = notificationEventRepository;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanExpiredEvents() {
        int deleted = notificationEventRepository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned {} expired notification events", deleted);
        }
    }
}
