package com.yingshi.server.service.upload;

import com.yingshi.server.domain.UploadState;
import com.yingshi.server.domain.UploadTaskEntity;
import com.yingshi.server.repository.UploadTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 过期上传任务清理调度器。
 * <p>
 * FR-10: 注入由 UploadService 改为 UploadCleanupService（拆分后职责分离）。
 * FR-5: 新增 scanOrphanedUploadObjects() 调度任务，每天 03:00 (Asia/Shanghai) 触发孤儿对象扫描。
 */
@Component
@ConditionalOnProperty(
        prefix = "app.upload.expired-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ExpiredUploadCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ExpiredUploadCleanupScheduler.class);

    private final UploadTaskRepository uploadTaskRepository;
    private final UploadCleanupService uploadCleanupService;

    public ExpiredUploadCleanupScheduler(
            UploadTaskRepository uploadTaskRepository,
            UploadCleanupService uploadCleanupService
    ) {
        this.uploadTaskRepository = uploadTaskRepository;
        this.uploadCleanupService = uploadCleanupService;
    }

    @Scheduled(
            fixedDelayString = "${app.upload.expired-cleanup.fixed-delay-millis:600000}",
            initialDelayString = "${app.upload.expired-cleanup.initial-delay-millis:120000}"
    )
    public void purgeExpiredUploadTasks() {
        List<UploadTaskEntity> expiredTasks = uploadTaskRepository
                .findByStateAndExpireAtBeforeOrderByExpireAtAsc(UploadState.WAITING, Instant.now());
        if (expiredTasks.isEmpty()) {
            return;
        }
        for (UploadTaskEntity task : expiredTasks) {
            try {
                uploadCleanupService.purgeExpiredTask(task.getId());
            } catch (Exception exception) {
                logger.warn("Failed to purge expired upload task {}", task.getId(), exception);
            }
        }
    }

    /**
     * FR-5: 每天 03:00 (Asia/Shanghai) 扫描 media 表中可能存在的孤儿对象并补偿。
     * 避开业务高峰，单次限量 500 条。
     */
    @Scheduled(cron = "${app.upload.orphan-scan.cron:0 0 3 * * *}", zone = "Asia/Shanghai")
    public void scanOrphanedUploadObjects() {
        try {
            uploadCleanupService.scanOrphanedObjects();
        } catch (Exception exception) {
            logger.warn("FR-5 orphan object scan failed", exception);
        }
    }
}
