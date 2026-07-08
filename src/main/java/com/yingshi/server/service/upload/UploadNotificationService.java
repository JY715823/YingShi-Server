package com.yingshi.server.service.upload;

import com.yingshi.server.domain.MediaType;
import com.yingshi.server.domain.UploadState;
import com.yingshi.server.domain.UploadTaskEntity;
import com.yingshi.server.repository.UploadTaskRepository;
import com.yingshi.server.service.push.PushDispatchSupport;
import com.yingshi.server.service.push.PushNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles push notification dispatch after upload operations complete.
 * Extracted from UploadService to separate notification concerns from upload processing.
 */
@Service
public class UploadNotificationService {

    private static final long NOTIFY_DELAY_MILLIS = 1800L;
    private static final long OPERATION_KEY_TTL_MILLIS = 24 * 60 * 60 * 1000L; // 24 hours
    private static final int CLEANUP_INTERVAL = 100;
    private static final Logger logger = LoggerFactory.getLogger(UploadNotificationService.class);
    private final ConcurrentHashMap<String, Long> notifiedUploadOperationKeys = new ConcurrentHashMap<>();
    private final AtomicInteger notificationCount = new AtomicInteger(0);

    private final UploadTaskRepository uploadTaskRepository;
    private final PushNotificationService pushNotificationService;

    public UploadNotificationService(
            UploadTaskRepository uploadTaskRepository,
            PushNotificationService pushNotificationService
    ) {
        this.uploadTaskRepository = uploadTaskRepository;
        this.pushNotificationService = pushNotificationService;
    }

    public void notifyIfCompleted(String libraryId, String actorUserId, UploadTaskEntity completedTask) {
        if (completedTask == null || completedTask.getState() != UploadState.SUCCESS) {
            return;
        }
        String operationId = normalizeNullable(completedTask.getOperationId());
        String completedTaskId = completedTask.getId();
        PushDispatchSupport.afterCommitAsync(() -> notifyAfterDelay(
                libraryId, actorUserId, operationId, completedTaskId
        ), NOTIFY_DELAY_MILLIS);
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
        if (notifiedUploadOperationKeys.putIfAbsent(operationKey, System.currentTimeMillis()) != null) {
            logger.debug("Skip duplicate upload operation push: {}", operationKey);
            return;
        }
        cleanupExpiredKeys();
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
                "照片内容有更新",
                "对方刚导入了" + mediaSummary + "。",
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

    private void cleanupExpiredKeys() {
        if (notificationCount.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - OPERATION_KEY_TTL_MILLIS;
        notifiedUploadOperationKeys.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }
}
