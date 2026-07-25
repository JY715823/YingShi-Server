package com.yingshi.server.service.upload;

import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.UploadState;
import com.yingshi.server.domain.UploadTaskEntity;
import com.yingshi.server.dto.upload.UploadTaskResponse;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.UploadTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * FR-10: 从原 UploadService 拆出的清理服务。
 * 承担 cancelUpload / purgeExpiredTask 主流程，
 * 并接入 FR-5 对象孤儿补偿扫描 scanOrphanedObjects。
 * <p>
 * FR-5 策略：
 * - 软删除 media（deletedAt != null）但 storagePath/previewObjectKey/coverObjectKey/originalObjectKey
 *   对应对象仍存在 → 删除对象（孤儿对象清理）
 * - 活跃 media 但 previewObjectKey/coverObjectKey/originalObjectKey 对应对象缺失 → 置空字段（孤儿 media 修复）
 * - 活跃 media 但 storagePath 对应对象缺失 → 仅 warn 日志（storagePath nullable=false，不可置空）
 * - 单次扫描限量 500 条，按 updatedAt DESC 优先扫描最近上传
 */
@Service
public class UploadCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(UploadCleanupService.class);
    private static final int ORPHAN_SCAN_BATCH_SIZE = 500;

    private final UploadTaskRepository uploadTaskRepository;
    private final MediaRepository mediaRepository;
    private final LocalMediaStorageService localMediaStorageService;
    private final UploadSupport uploadSupport;
    private final UploadHistoryService uploadHistoryService;

    public UploadCleanupService(
            UploadTaskRepository uploadTaskRepository,
            MediaRepository mediaRepository,
            LocalMediaStorageService localMediaStorageService,
            UploadSupport uploadSupport,
            UploadHistoryService uploadHistoryService
    ) {
        this.uploadTaskRepository = uploadTaskRepository;
        this.mediaRepository = mediaRepository;
        this.localMediaStorageService = localMediaStorageService;
        this.uploadSupport = uploadSupport;
        this.uploadHistoryService = uploadHistoryService;
    }

    @Transactional
    public UploadTaskResponse cancelUpload(String uploadId, AuthenticatedUser currentUser) {
        UploadTaskEntity task = uploadSupport.requireUploadTask(uploadId, currentUser.libraryId());
        if (task.getState() == UploadState.SUCCESS) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.UPLOAD_ALREADY_COMPLETED, "Completed upload task cannot be cancelled.");
        }
        if (task.getState() != UploadState.CANCELLED) {
            task.setState(UploadState.CANCELLED);
            task.setErrorMessage("Upload task was cancelled.");
            task.setCompletedAt(Instant.now());
            uploadTaskRepository.save(task);
            uploadSupport.deleteTaskStorageObject(task.getStoredPath());
        }
        return uploadHistoryService.toUploadTaskResponse(task);
    }

    @Transactional
    public void purgeExpiredTask(String taskId) {
        uploadTaskRepository.findById(taskId).ifPresent(task -> {
            uploadSupport.deleteTaskStorageObject(task.getStoredPath());
            uploadTaskRepository.delete(task);
        });
    }

    /**
     * FR-5: 扫描 media 表中可能存在的孤儿对象并补偿。
     * <p>
     * 触发频率：每天 03:00 一次（由 ExpiredUploadCleanupScheduler 调用）。
     * 单次扫描限量 ORPHAN_SCAN_BATCH_SIZE 条，按 updatedAt DESC 排序。
     * 不影响现有过期任务清理逻辑（state=WAITING AND expireAt<now）。
     */
    @Transactional
    public OrphanScanResult scanOrphanedObjects() {
        List<MediaEntity> candidates = mediaRepository.findTopByOrderByUpdatedAtDesc(
                PageRequest.of(0, ORPHAN_SCAN_BATCH_SIZE)
        );
        int orphanObjectsDeleted = 0;
        int orphanMediaFieldsCleared = 0;
        int brokenMediaCount = 0;
        int updatedMediaCount = 0;

        for (MediaEntity media : candidates) {
            boolean modified = false;
            boolean softDeleted = media.getDeletedAt() != null;

            // storagePath 处理
            String storagePath = media.getStoragePath();
            if (storagePath != null && !storagePath.isBlank()) {
                boolean exists = localMediaStorageService.objectExists(storagePath);
                if (softDeleted && exists) {
                    // 孤儿对象：media 已软删除但对象仍存在 → 删除对象
                    uploadSupport.deleteTaskStorageObject(storagePath);
                    orphanObjectsDeleted++;
                } else if (!softDeleted && !exists) {
                    // 孤儿 media：media 活跃但 storagePath 对象缺失
                    // storagePath 字段 nullable=false，不可置空，仅 warn 日志
                    logger.warn("Orphan media detected: media={} storagePath object missing but field is non-nullable; left unchanged",
                            media.getId());
                    brokenMediaCount++;
                }
            }

            // previewObjectKey 处理（nullable）
            String previewKey = media.getPreviewObjectKey();
            if (previewKey != null && !previewKey.isBlank()) {
                boolean exists = localMediaStorageService.objectExists(previewKey);
                if (softDeleted && exists) {
                    uploadSupport.deleteTaskStorageObject(previewKey);
                    orphanObjectsDeleted++;
                } else if (!softDeleted && !exists) {
                    media.setPreviewObjectKey(null);
                    orphanMediaFieldsCleared++;
                    modified = true;
                }
            }

            // coverObjectKey 处理（nullable）
            String coverKey = media.getCoverObjectKey();
            if (coverKey != null && !coverKey.isBlank()) {
                boolean exists = localMediaStorageService.objectExists(coverKey);
                if (softDeleted && exists) {
                    uploadSupport.deleteTaskStorageObject(coverKey);
                    orphanObjectsDeleted++;
                } else if (!softDeleted && !exists) {
                    media.setCoverObjectKey(null);
                    orphanMediaFieldsCleared++;
                    modified = true;
                }
            }

            // originalObjectKey 处理（nullable）
            String originalKey = media.getOriginalObjectKey();
            if (originalKey != null && !originalKey.isBlank()) {
                boolean exists = localMediaStorageService.objectExists(originalKey);
                if (softDeleted && exists) {
                    uploadSupport.deleteTaskStorageObject(originalKey);
                    orphanObjectsDeleted++;
                } else if (!softDeleted && !exists) {
                    media.setOriginalObjectKey(null);
                    orphanMediaFieldsCleared++;
                    modified = true;
                }
            }

            if (modified) {
                mediaRepository.save(media);
                updatedMediaCount++;
            }
        }

        OrphanScanResult result = new OrphanScanResult(
                candidates.size(),
                orphanObjectsDeleted,
                orphanMediaFieldsCleared,
                brokenMediaCount,
                updatedMediaCount
        );
        logger.info("FR-5 orphan scan completed: scanned={}, orphanObjectsDeleted={}, orphanMediaFieldsCleared={}, brokenMedia={}, updatedMedia={}",
                result.scanned(), result.orphanObjectsDeleted(), result.orphanMediaFieldsCleared(),
                result.brokenMediaCount(), result.updatedMediaCount());
        return result;
    }

    /**
     * FR-5 孤儿扫描结果，用于日志和测试断言。
     */
    public record OrphanScanResult(
            int scanned,
            int orphanObjectsDeleted,
            int orphanMediaFieldsCleared,
            int brokenMediaCount,
            int updatedMediaCount
    ) {}
}
