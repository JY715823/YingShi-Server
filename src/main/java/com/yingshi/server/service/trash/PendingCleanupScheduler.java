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
}
