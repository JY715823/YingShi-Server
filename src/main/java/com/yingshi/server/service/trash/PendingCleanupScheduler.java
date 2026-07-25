package com.yingshi.server.service.trash;

import com.yingshi.server.domain.TrashItemEntity;
import com.yingshi.server.domain.TrashItemState;
import com.yingshi.server.repository.TrashItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "app.trash.pending-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PendingCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PendingCleanupScheduler.class);

    /**
     * P1-3 自动清理: IN_TRASH 状态的回收站项目保留天数。
     * 超过此天数的项目会被自动彻底删除(覆盖 photo 回收站和 life 回收站)。
     */
    private static final int IN_TRASH_RETENTION_DAYS = 30;

    private final TrashItemRepository trashItemRepository;
    private final TrashService trashService;

    public PendingCleanupScheduler(
            TrashItemRepository trashItemRepository,
            TrashService trashService
    ) {
        this.trashItemRepository = trashItemRepository;
        this.trashService = trashService;
    }

    @Scheduled(
            fixedDelayString = "${app.trash.pending-cleanup.fixed-delay-millis:600000}",
            initialDelayString = "${app.trash.pending-cleanup.initial-delay-millis:60000}"
    )
    public void purgeExpiredPendingCleanupItems() {
        List<TrashItemEntity> expiredItems = trashItemRepository.findByStateAndUndoDeadlineAtBeforeOrderByUndoDeadlineAtAsc(
                TrashItemState.PENDING_CLEANUP,
                Instant.now()
        );
        if (expiredItems.isEmpty()) {
            return;
        }

        for (TrashItemEntity item : expiredItems) {
            try {
                trashService.purgeExpiredPendingCleanupItem(item.getId(), item.getLibraryId());
            } catch (Exception exception) {
                logger.warn("Failed to purge expired pending cleanup item {}", item.getId(), exception);
            }
        }
    }

    /**
     * P1-3 自动清理: 每天凌晨 3 点扫描 IN_TRASH 状态超过 30 天的回收站项目, 转入 24h 待清理窗口。
     * 用户需求: 过期项目先进入 24h 待清理(给用户最后一次撤销机会), 24h 后再彻底删除。
     * 覆盖 photo 回收站和 life 回收站(lifeCategory IS NOT NULL)的所有项目。
     * 使用 cron 表达式 "0 0 3 * * *" = 每天 03:00 执行。
     */
    @Scheduled(
            cron = "${app.trash.in-trash-cleanup.cron:0 0 3 * * *}",
            zone = "Asia/Shanghai"
    )
    public void purgeExpiredInTrashItems() {
        Instant threshold = Instant.now().minus(IN_TRASH_RETENTION_DAYS, ChronoUnit.DAYS);
        List<TrashItemEntity> expiredItems = trashItemRepository.findByStateAndDeletedAtBeforeOrderByDeletedAtAsc(
                TrashItemState.IN_TRASH,
                threshold
        );
        if (expiredItems.isEmpty()) {
            logger.info("purgeExpiredInTrashItems: no items older than {} days, skip", IN_TRASH_RETENTION_DAYS);
            return;
        }

        logger.info("purgeExpiredInTrashItems: found {} items older than {} days, transitioning to pending cleanup", expiredItems.size(), IN_TRASH_RETENTION_DAYS);
        int transitioned = 0;
        for (TrashItemEntity item : expiredItems) {
            try {
                trashService.transitionExpiredInTrashToPending(item.getId(), item.getLibraryId());
                transitioned++;
            } catch (Exception exception) {
                logger.warn("Failed to transition expired in-trash item {} to pending cleanup", item.getId(), exception);
            }
        }
        logger.info("purgeExpiredInTrashItems: transitioned {} of {} items to pending cleanup", transitioned, expiredItems.size());
    }
}
