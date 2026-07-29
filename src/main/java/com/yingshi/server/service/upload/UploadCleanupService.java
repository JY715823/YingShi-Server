package com.yingshi.server.service.upload;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.OrphanQuarantineEntity;
import com.yingshi.server.domain.OrphanScanCursorEntity;
import com.yingshi.server.domain.PurgeIntentEntity;
import com.yingshi.server.domain.UploadState;
import com.yingshi.server.domain.UploadTaskEntity;
import com.yingshi.server.dto.upload.UploadTaskResponse;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.OrphanQuarantineRepository;
import com.yingshi.server.repository.OrphanScanCursorRepository;
import com.yingshi.server.repository.PurgeIntentRepository;
import com.yingshi.server.repository.UploadTaskRepository;
import com.yingshi.server.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * FR-10: 从原 UploadService 拆出的清理服务。
 * 承担 cancelUpload / purgeExpiredTask 主流程，
 * 并接入 FR-5 对象孤儿补偿扫描 scanOrphanedObjects。
 * <p>
 * R2-D 改造:
 * - 持久 cursor (orphan_scan_cursor): 全表分批扫描，位置可恢复，不再"扫描即删除"。
 * - quarantine 隔离 (orphan_quarantine): 扫描只标记，隔离 7 天 (跨一个备份周期) 后才真正删除。
 * - dry-run: 默认开启，仅扫描统计不写库；关闭后才记录 quarantine。
 * - 复用 PurgeIntent outbox: 真正删除走 PurgeIntentProcessor 异步重试，不再直接调 deleteTaskStorageObject。
 */
@Service
public class UploadCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(UploadCleanupService.class);
    private static final int ORPHAN_SCAN_BATCH_SIZE = 5000;
    private static final Duration QUARANTINE_WINDOW = Duration.ofDays(7);
    private static final String CURSOR_ID_DEFAULT = "default";
    private static final String QUARANTINE_STATUS = "QUARANTINED";
    private static final String QUARANTINE_STATUS_PURGED = "PURGED";

    private final UploadTaskRepository uploadTaskRepository;
    private final MediaRepository mediaRepository;
    private final LocalMediaStorageService localMediaStorageService;
    private final UploadSupport uploadSupport;
    private final UploadHistoryService uploadHistoryService;
    private final OrphanScanCursorRepository orphanScanCursorRepository;
    private final OrphanQuarantineRepository orphanQuarantineRepository;
    private final PurgeIntentRepository purgeIntentRepository;
    private final AuditLogService auditLogService;

    public UploadCleanupService(
            UploadTaskRepository uploadTaskRepository,
            MediaRepository mediaRepository,
            LocalMediaStorageService localMediaStorageService,
            UploadSupport uploadSupport,
            UploadHistoryService uploadHistoryService,
            OrphanScanCursorRepository orphanScanCursorRepository,
            OrphanQuarantineRepository orphanQuarantineRepository,
            PurgeIntentRepository purgeIntentRepository,
            AuditLogService auditLogService
    ) {
        this.uploadTaskRepository = uploadTaskRepository;
        this.mediaRepository = mediaRepository;
        this.localMediaStorageService = localMediaStorageService;
        this.uploadSupport = uploadSupport;
        this.uploadHistoryService = uploadHistoryService;
        this.orphanScanCursorRepository = orphanScanCursorRepository;
        this.orphanQuarantineRepository = orphanQuarantineRepository;
        this.purgeIntentRepository = purgeIntentRepository;
        this.auditLogService = auditLogService;
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
     * R2-D-1/2/3/4/5/8: 扫描 media 表中可能存在的孤儿对象。
     * <p>
     * 触发频率：每天 03:00 一次（由 ExpiredUploadCleanupScheduler 调用）。
     * 使用持久 cursor (orphan_scan_cursor) 分批扫描，每次 ORPHAN_SCAN_BATCH_SIZE 条，
     * 按 (updatedAt ASC, id ASC) 向前推进；全表扫完后 cursor 重置从头开始。
     * <p>
     * 扫描时只标记 quarantine (隔离 7 天)，不立即删除；dryRun=true 时仅统计不写库。
     * 真正删除由 purgeQuarantined 触发，走 PurgeIntent outbox 异步执行。
     */
    @Transactional
    public OrphanScanResult scanOrphanedObjects(boolean dryRun) {
        OrphanScanCursorEntity cursor = orphanScanCursorRepository.findById(CURSOR_ID_DEFAULT)
                .orElseGet(() -> {
                    OrphanScanCursorEntity fresh = new OrphanScanCursorEntity();
                    fresh.setId(CURSOR_ID_DEFAULT);
                    return fresh;
                });

        Instant cursorUpdatedAt = cursor.getLastScannedUpdatedAt() != null
                ? cursor.getLastScannedUpdatedAt()
                : Instant.EPOCH;
        String cursorId = cursor.getLastScannedId();

        List<MediaEntity> candidates = mediaRepository.findOrphanCandidatesSince(
                cursorUpdatedAt,
                cursorId,
                PageRequest.of(0, ORPHAN_SCAN_BATCH_SIZE)
        );

        if (candidates.isEmpty()) {
            // 全表扫完，重置 cursor 以便下次从头开始
            if (!dryRun) {
                cursor.setLastScannedUpdatedAt(null);
                cursor.setLastScannedId(null);
                orphanScanCursorRepository.save(cursor);
            }
            logger.info("R2-D orphan scan: reached end of table, cursor reset. dryRun={}", dryRun);
            return new OrphanScanResult(0, 0, 0, 0, 0, dryRun, true);
        }

        int orphanObjectsQuarantined = 0;
        int orphanMediaFieldsCleared = 0;
        int brokenMediaCount = 0;
        int updatedMediaCount = 0;
        Instant now = Instant.now();
        Instant quarantineUntil = now.plus(QUARANTINE_WINDOW);

        for (MediaEntity media : candidates) {
            boolean modified = false;
            boolean softDeleted = media.getDeletedAt() != null;
            String mediaId = media.getId();
            String libraryId = media.getLibraryId();

            // storagePath 处理 (nullable=false, 不可置空)
            String storagePath = media.getStoragePath();
            if (storagePath != null && !storagePath.isBlank()) {
                boolean exists = localMediaStorageService.objectExists(storagePath);
                if (softDeleted && exists) {
                    // 孤儿对象: media 已软删除但对象仍存在 → 标记 quarantine (不立即删除)
                    if (!dryRun) {
                        orphanObjectsQuarantined += recordQuarantine(mediaId, storagePath, now, quarantineUntil);
                    } else {
                        orphanObjectsQuarantined++;
                        recordOrphanDetectedAudit(mediaId, libraryId, storagePath);
                    }
                } else if (!softDeleted && !exists) {
                    // 孤儿 media: storagePath 对象缺失但字段不可置空，仅 warn
                    logger.warn("Orphan media detected: media={} storagePath object missing but field is non-nullable; left unchanged",
                            mediaId);
                    brokenMediaCount++;
                }
            }

            // previewObjectKey 处理 (nullable)
            String previewKey = media.getPreviewObjectKey();
            if (previewKey != null && !previewKey.isBlank()) {
                boolean exists = localMediaStorageService.objectExists(previewKey);
                if (softDeleted && exists) {
                    if (!dryRun) {
                        orphanObjectsQuarantined += recordQuarantine(mediaId, previewKey, now, quarantineUntil);
                    } else {
                        orphanObjectsQuarantined++;
                        recordOrphanDetectedAudit(mediaId, libraryId, previewKey);
                    }
                } else if (!softDeleted && !exists) {
                    if (!dryRun) {
                        media.setPreviewObjectKey(null);
                    }
                    orphanMediaFieldsCleared++;
                    modified = true;
                }
            }

            // coverObjectKey 处理 (nullable)
            String coverKey = media.getCoverObjectKey();
            if (coverKey != null && !coverKey.isBlank()) {
                boolean exists = localMediaStorageService.objectExists(coverKey);
                if (softDeleted && exists) {
                    if (!dryRun) {
                        orphanObjectsQuarantined += recordQuarantine(mediaId, coverKey, now, quarantineUntil);
                    } else {
                        orphanObjectsQuarantined++;
                        recordOrphanDetectedAudit(mediaId, libraryId, coverKey);
                    }
                } else if (!softDeleted && !exists) {
                    if (!dryRun) {
                        media.setCoverObjectKey(null);
                    }
                    orphanMediaFieldsCleared++;
                    modified = true;
                }
            }

            // originalObjectKey 处理 (nullable)
            String originalKey = media.getOriginalObjectKey();
            if (originalKey != null && !originalKey.isBlank()) {
                boolean exists = localMediaStorageService.objectExists(originalKey);
                if (softDeleted && exists) {
                    if (!dryRun) {
                        orphanObjectsQuarantined += recordQuarantine(mediaId, originalKey, now, quarantineUntil);
                    } else {
                        orphanObjectsQuarantined++;
                        recordOrphanDetectedAudit(mediaId, libraryId, originalKey);
                    }
                } else if (!softDeleted && !exists) {
                    if (!dryRun) {
                        media.setOriginalObjectKey(null);
                    }
                    orphanMediaFieldsCleared++;
                    modified = true;
                }
            }

            if (modified && !dryRun) {
                mediaRepository.save(media);
                updatedMediaCount++;
            }
        }

        // 推进 cursor 到本批最后一条
        MediaEntity last = candidates.get(candidates.size() - 1);
        boolean scanCompleted = candidates.size() < ORPHAN_SCAN_BATCH_SIZE;
        if (!dryRun) {
            if (scanCompleted) {
                // 本批未填满，说明已到表尾，重置 cursor 从头开始
                cursor.setLastScannedUpdatedAt(null);
                cursor.setLastScannedId(null);
            } else {
                cursor.setLastScannedUpdatedAt(last.getUpdatedAt());
                cursor.setLastScannedId(last.getId());
            }
            orphanScanCursorRepository.save(cursor);
        }

        OrphanScanResult result = new OrphanScanResult(
                candidates.size(),
                orphanObjectsQuarantined,
                orphanMediaFieldsCleared,
                brokenMediaCount,
                updatedMediaCount,
                dryRun,
                scanCompleted
        );
        logger.info("R2-D orphan scan completed: dryRun={}, scanned={}, orphanObjectsQuarantined={}, orphanMediaFieldsCleared={}, brokenMedia={}, updatedMedia={}, scanCompleted={}",
                result.dryRun(), result.scanned(), result.orphanObjectsQuarantined(), result.orphanMediaFieldsCleared(),
                result.brokenMediaCount(), result.updatedMediaCount(), result.scanCompleted());
        return result;
    }

    /**
     * R2-D-3/4: 记录 quarantine 隔离条目。已存在的 (mediaId, objectKey) 不重复记录。
     * @return 1 表示新增隔离，0 表示已存在跳过
     */
    private int recordQuarantine(String mediaId, String objectKey, Instant detectedAt, Instant quarantineUntil) {
        if (orphanQuarantineRepository.existsByMediaIdAndObjectKey(mediaId, objectKey)) {
            return 0;
        }
        OrphanQuarantineEntity quarantine = new OrphanQuarantineEntity();
        quarantine.setMediaId(mediaId);
        quarantine.setObjectKey(objectKey);
        quarantine.setDetectedAt(detectedAt);
        quarantine.setQuarantineUntil(quarantineUntil);
        quarantine.setStatus(QUARANTINE_STATUS);
        orphanQuarantineRepository.save(quarantine);
        return 1;
    }

    /**
     * R2-D-6: scanOrphanedObjects 在 dryRun=true 检测到孤儿对象时写一条 audit_log，
     * 便于运维在不开启真正清理的情况下追溯孤儿出现频率与范围。
     */
    private void recordOrphanDetectedAudit(String mediaId, String libraryId, String objectKey) {
        auditLogService.record(
                "system",
                libraryId == null ? "system" : libraryId,
                "ORPHAN_DETECTED",
                "ORPHAN_OBJECT",
                mediaId,
                "mediaId=" + mediaId + ", objectKey=" + objectKey
        );
    }

    /**
     * R2-D-3/4/5/8: 清理已过隔离期的孤儿对象。
     * 扫描 orphan_quarantine 表中 status=QUARANTINED 且 quarantineUntil < now 的记录，
     * 为每条记录创建 PurgeIntent (交由 PurgeIntentProcessor 异步删除)，并将状态标记为 PURGED。
     * 不再直接调用 deleteTaskStorageObject，避免删除失败导致状态不一致。
     */
    @Transactional
    public int purgeQuarantined() {
        List<OrphanQuarantineEntity> expired = orphanQuarantineRepository
                .findByStatusAndQuarantineUntilBefore(QUARANTINE_STATUS, Instant.now());
        if (expired.isEmpty()) {
            return 0;
        }
        int purged = 0;
        for (OrphanQuarantineEntity entry : expired) {
            try {
                recordPurgeIntentForOrphan(entry.getMediaId(), entry.getObjectKey(), entry.getObjectKey());
                entry.setStatus(QUARANTINE_STATUS_PURGED);
                orphanQuarantineRepository.save(entry);
                purged++;
            } catch (Exception e) {
                // 单条失败不影响其他条目，下次 purge 会重试 (status 仍为 QUARANTINED)
                // R2-D-6: 失败时同步写入 audit_log 表，便于运维追溯
                logger.error("purgeQuarantined: failed to record purge intent for quarantine id={} mediaId={} objectKey={}",
                        entry.getId(), entry.getMediaId(), entry.getObjectKey(), e);
                auditLogService.record(
                        "system",
                        "system",
                        "ORPHAN_PURGE_FAILED",
                        "ORPHAN_OBJECT",
                        entry.getMediaId(),
                        "mediaId=" + entry.getMediaId() + ", objectKey=" + entry.getObjectKey() + ", error=" + e.getMessage()
                );
            }
        }
        logger.info("purgeQuarantined: purged={} of {} expired quarantine entries", purged, expired.size());
        return purged;
    }

    /**
     * R2-D-5/8: 为孤儿对象写 PurgeIntent，复用 PurgeIntentProcessor 异步删除对象存储。
     * trashItemId 使用哨兵值 "orphan:<mediaId>" (PurgeIntentProcessor.maybeDeleteTrashItem
     * 会对该 id findById 返回空后安全跳过，不影响对象删除)。
     */
    private void recordPurgeIntentForOrphan(String mediaId, String objectKey, String libraryId) {
        PurgeIntentEntity intent = new PurgeIntentEntity();
        intent.setId(IdGenerator.newId("purge"));
        intent.setTrashItemId("orphan:" + (mediaId == null ? "unknown" : mediaId));
        intent.setLibraryId(libraryId == null ? "" : libraryId);
        intent.setObjectType("ORPHAN_OBJECT");
        intent.setStoragePath(objectKey);
        intent.setObjectKey(objectKey);
        intent.setMediaId(mediaId);
        intent.setState("PENDING");
        intent.setAttempts(0);
        intent.setMaxAttempts(5);
        intent.setNextRetryAt(Instant.now());
        purgeIntentRepository.save(intent);
    }

    /**
     * FR-5 / R2-D 孤儿扫描结果，用于日志和测试断言。
     *
     * @param scanned                   本批扫描的 media 数量
     * @param orphanObjectsQuarantined  本批新隔离的孤儿对象数量 (dryRun 时为检测到的数量)
     * @param orphanMediaFieldsCleared  本批修复的孤儿 media 字段数量 (活跃 media 对象缺失 → 置空)
     * @param brokenMediaCount          storagePath 对象缺失但不可置空的 broken media 数量
     * @param updatedMediaCount         本批更新的 media 数量
     * @param dryRun                    是否为 dry-run 模式 (仅统计不写库)
     * @param scanCompleted             是否扫到表尾 (cursor 将重置)
     */
    public record OrphanScanResult(
            int scanned,
            int orphanObjectsQuarantined,
            int orphanMediaFieldsCleared,
            int brokenMediaCount,
            int updatedMediaCount,
            boolean dryRun,
            boolean scanCompleted
    ) {}
}
