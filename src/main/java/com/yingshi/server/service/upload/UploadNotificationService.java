package com.yingshi.server.service.upload;

import com.yingshi.server.domain.MediaType;
import com.yingshi.server.domain.NotificationDedupEntity;
import com.yingshi.server.domain.UploadState;
import com.yingshi.server.domain.UploadTaskEntity;
import com.yingshi.server.repository.NotificationDedupRepository;
import com.yingshi.server.repository.UploadTaskRepository;
import com.yingshi.server.service.push.PushDispatchSupport;
import com.yingshi.server.service.push.PushNotificationService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles push notification dispatch after upload operations complete.
 * FR-6: Dedup backed by DB (notification_dedup table) with L1 ConcurrentHashMap cache.
 */
@Service
public class UploadNotificationService {

    private static final long DEDUP_RETENTION_HOURS = 24;
    private static final Logger logger = LoggerFactory.getLogger(UploadNotificationService.class);

    /** L1 cache: fast in-process dedup (avoids DB round-trip for hot path). */
    private final ConcurrentHashMap<String, Boolean> l1DedupCache = new ConcurrentHashMap<>();

    private final UploadTaskRepository uploadTaskRepository;
    private final PushNotificationService pushNotificationService;
    private final NotificationDedupRepository notificationDedupRepository;

    @Value("${app.push.dedup.multi-instance:false}")
    private boolean multiInstanceDeployment;

    @Value("${app.push.upload-notification-delay-millis:1800}")
    private long notificationDelayMillis;

    public UploadNotificationService(
            UploadTaskRepository uploadTaskRepository,
            PushNotificationService pushNotificationService,
            NotificationDedupRepository notificationDedupRepository
    ) {
        this.uploadTaskRepository = uploadTaskRepository;
        this.pushNotificationService = pushNotificationService;
        this.notificationDedupRepository = notificationDedupRepository;
    }

    @PostConstruct
    public void logDedupBackendOnStartup() {
        if (multiInstanceDeployment) {
            logger.info("Upload notification dedup: DB-backed (notification_dedup) + L1 cache, multi-instance mode.");
        } else {
            logger.info("Upload notification dedup: DB-backed (notification_dedup) + L1 cache, single-instance mode.");
        }
    }

    /**
     * Scheduled cleanup: remove dedup entries older than 24 hours.
     * Also clears the L1 cache to keep it in sync.
     */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 600_000L) // every hour
    @Transactional
    public void cleanupExpiredDedupEntries() {
        Instant cutoff = Instant.now().minus(DEDUP_RETENTION_HOURS, ChronoUnit.HOURS);
        int deleted = notificationDedupRepository.deleteExpiredBefore(cutoff);
        l1DedupCache.clear(); // L1 cache is best-effort, full clear on cleanup
        if (deleted > 0) {
            logger.info("Notification dedup cleanup: deleted {} expired entries", deleted);
        }
    }

    public void notifyIfCompleted(String libraryId, String actorUserId, UploadTaskEntity completedTask) {
        if (completedTask == null || completedTask.getState() != UploadState.SUCCESS) {
            return;
        }
        String operationId = normalizeNullable(completedTask.getOperationId());
        String completedTaskId = completedTask.getId();
        PushDispatchSupport.afterCommitAsync(() -> notifyAfterDelay(
                libraryId, actorUserId, operationId, completedTaskId
        ), Math.max(0L, notificationDelayMillis));
    }

    private void notifyAfterDelay(
            String libraryId,
            String actorUserId,
            String operationId,
            String completedTaskId
    ) {
        List<UploadTaskEntity> operationTasks = operationId == null
                ? uploadTaskRepository.findByIdAndLibraryId(completedTaskId, libraryId).map(List::of).orElse(List.of())
                : uploadTaskRepository.findByLibraryIdAndOperationId(libraryId, operationId);
        if (operationTasks.isEmpty()) {
            return;
        }
        UploadTaskEntity completedTask = operationTasks.stream()
                .filter(task -> completedTaskId.equals(task.getId()))
                .findFirst()
                .orElse(operationTasks.get(0));
        if (completedTask.getState() != UploadState.SUCCESS) {
            return;
        }
        int expectedCount = operationTasks.stream()
                .map(UploadTaskEntity::getOperationMediaCount)
                .filter(count -> count != null && count > 0)
                .max(Integer::compareTo)
                .orElse(operationTasks.size());
        if (operationId != null && operationTasks.size() < expectedCount) {
            return;
        }
        long finishedCount = operationTasks.stream()
                .filter(task -> task.getState() == UploadState.SUCCESS || task.getState() == UploadState.CANCELLED || task.getState() == UploadState.FAILED)
                .count();
        if (expectedCount > 1 && finishedCount < expectedCount) {
            return;
        }
        List<UploadTaskEntity> successfulTasks = operationTasks.stream()
                .filter(task -> task.getState() == UploadState.SUCCESS)
                .toList();
        if (successfulTasks.isEmpty()) {
            return;
        }
        // Skip photos push for life console uploads — life push is sent by LifeConsoleService instead
        String operationType = completedTask.getOperationType();
        if ("LIFE_CONSOLE".equals(operationType)) {
            logger.debug("Skip photos push for LIFE_CONSOLE operation: operationId={}", operationId);
            return;
        }
        String operationKey = libraryId + ":" + (operationId == null ? completedTask.getId() : operationId);

        // FR-6: L1 cache fast-path check (avoids DB round-trip for hot path)
        if (l1DedupCache.putIfAbsent(operationKey, Boolean.TRUE) != null) {
            logger.debug("Skip duplicate upload operation push (L1): {}", operationKey);
            return;
        }

        // FR-6: L2 DB dedup — INSERT with PK conflict detection
        try {
            notificationDedupRepository.save(new NotificationDedupEntity(operationKey, libraryId));
        } catch (DataIntegrityViolationException e) {
            logger.debug("Skip duplicate upload operation push (DB): {}", operationKey);
            return;
        }
        int imageCount = (int) successfulTasks.stream().filter(task -> task.getMediaType() == MediaType.IMAGE).count();
        int videoCount = (int) successfulTasks.stream().filter(task -> task.getMediaType() == MediaType.VIDEO).count();
        String mediaSummary = uploadMediaSummary(imageCount, videoCount);
        String targetMediaId = successfulTasks.stream()
                .map(UploadTaskEntity::getMediaId)
                .filter(mediaId -> mediaId != null && !mediaId.isBlank())
                .findFirst()
                .orElse(completedTask.getMediaId());
        String targetRoute = targetMediaId == null || targetMediaId.isBlank() ? "photos" : "photos:media:" + targetMediaId;
        pushNotificationService.notifyPhotoChanged(
                libraryId,
                actorUserId,
                PushNotificationService.CATEGORY_PHOTOS_CONTENT_UPDATE,
                "照片",
                "对方上传了" + mediaSummary + "。",
                targetRoute,
                "upload:" + (operationId == null ? completedTask.getId() : operationId),
                "upload:" + (operationId == null ? completedTask.getId() : operationId)
        );
    }

    private String uploadMediaSummary(int imageCount, int videoCount) {
        if (imageCount > 0 && videoCount > 0) {
            return imageCount + "张照片和" + videoCount + "个视频";
        }
        if (videoCount > 0) {
            return videoCount + "个视频";
        }
        return imageCount + "张照片";
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
