package com.yingshi.server.service.trash;

import com.yingshi.server.domain.PurgeIntentEntity;
import com.yingshi.server.domain.TrashItemEntity;
import com.yingshi.server.repository.PurgeIntentRepository;
import com.yingshi.server.repository.TrashItemRepository;
import com.yingshi.server.service.upload.LocalMediaStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * R3-TRASH-002: Asynchronous processor for purge_intents outbox table.
 *
 * Scans for PENDING / FAILED intents whose next_retry_at has elapsed and attempts
 * the underlying object storage deletion. Each intent follows an idempotent state
 * machine: PENDING → IN_PROGRESS → COMPLETED (or FAILED with exponential backoff).
 *
 * After every intent for a given trash item reaches a terminal state
 * (COMPLETED, or FAILED after exhausting max attempts), the trash item itself
 * is removed from trash_items, completing the deferred purge lifecycle.
 */
@Component
public class PurgeIntentProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PurgeIntentProcessor.class);

    private static final String STATE_PENDING = "PENDING";
    private static final String STATE_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATE_COMPLETED = "COMPLETED";
    private static final String STATE_FAILED = "FAILED";

    private static final int LAST_ERROR_MAX_LENGTH = 1000;

    private final PurgeIntentRepository purgeIntentRepository;
    private final LocalMediaStorageService localMediaStorageService;
    private final TrashItemRepository trashItemRepository;

    public PurgeIntentProcessor(
            PurgeIntentRepository purgeIntentRepository,
            LocalMediaStorageService localMediaStorageService,
            TrashItemRepository trashItemRepository
    ) {
        this.purgeIntentRepository = purgeIntentRepository;
        this.localMediaStorageService = localMediaStorageService;
        this.trashItemRepository = trashItemRepository;
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processPendingIntents() {
        List<PurgeIntentEntity> pending = purgeIntentRepository.findByStateInAndNextRetryAtBefore(
                List.of(STATE_PENDING, STATE_FAILED),
                Instant.now()
        );
        if (pending.isEmpty()) {
            return;
        }
        logger.info("processPendingIntents: processing {} purge intent(s)", pending.size());
        for (PurgeIntentEntity intent : pending) {
            try {
                processIntent(intent);
            } catch (Exception e) {
                // 单个 intent 失败不影响其他 intent 处理
                // R2-D-6: 升级为 error 级别，与 UploadCleanupService.purgeQuarantined 单条失败日志一致
                logger.error("processPendingIntents: unexpected failure for intent {} trashItemId={}",
                        intent.getId(), intent.getTrashItemId(), e);
            }
        }
    }

    private void processIntent(PurgeIntentEntity intent) {
        intent.setState(STATE_IN_PROGRESS);
        intent.setAttempts(intent.getAttempts() + 1);
        purgeIntentRepository.save(intent);

        try {
            deleteObject(intent);
            intent.setState(STATE_COMPLETED);
            intent.setCompletedAt(Instant.now());
            logger.info("processIntent: completed intent {} trashItemId={} mediaId={}",
                    intent.getId(), intent.getTrashItemId(), intent.getMediaId());
        } catch (Exception e) {
            intent.setState(STATE_FAILED);
            intent.setLastError(truncate(safeMessage(e), LAST_ERROR_MAX_LENGTH));
            if (intent.getAttempts() < intent.getMaxAttempts()) {
                long backoffSeconds = (long) Math.pow(2, intent.getAttempts());
                intent.setNextRetryAt(Instant.now().plus(Duration.ofSeconds(backoffSeconds)));
                logger.warn("processIntent: intent {} failed (attempt {}/{}), will retry in {}s",
                        intent.getId(), intent.getAttempts(), intent.getMaxAttempts(), backoffSeconds, e);
            } else {
                logger.warn("processIntent: intent {} exhausted max attempts ({}, marking terminal FAILED",
                        intent.getId(), intent.getMaxAttempts(), e);
            }
        }
        purgeIntentRepository.save(intent);

        // 尝试清理 trash item: 所有 intent 终态后删除 trash item
        maybeDeleteTrashItem(intent.getTrashItemId());
    }

    private void deleteObject(PurgeIntentEntity intent) {
        // deleteStoredMediaFiles 处理 local/remote 两种 provider，对象不存在视为幂等成功（不抛异常）。
        // mediaId 作为 cacheKey，用于远程 provider 的派生对象前缀扫描。
        localMediaStorageService.deleteStoredMediaFiles(intent.getStoragePath(), intent.getMediaId());
    }

    private void maybeDeleteTrashItem(String trashItemId) {
        List<PurgeIntentEntity> intents = purgeIntentRepository.findByTrashItemId(trashItemId);
        if (intents.isEmpty()) {
            return;
        }
        boolean allTerminal = intents.stream().allMatch(this::isTerminal);
        if (!allTerminal) {
            return;
        }
        trashItemRepository.findById(trashItemId).ifPresent(item -> {
            trashItemRepository.delete(item);
            logger.info("maybeDeleteTrashItem: all purge intents terminal, deleted trash item {}", trashItemId);
        });
    }

    private boolean isTerminal(PurgeIntentEntity intent) {
        if (STATE_COMPLETED.equals(intent.getState())) {
            return true;
        }
        if (STATE_FAILED.equals(intent.getState()) && intent.getAttempts() >= intent.getMaxAttempts()) {
            return true;
        }
        return false;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String safeMessage(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
