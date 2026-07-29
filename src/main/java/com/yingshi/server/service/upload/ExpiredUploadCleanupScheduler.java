package com.yingshi.server.service.upload;

import com.yingshi.server.domain.UploadState;
import com.yingshi.server.domain.UploadTaskEntity;
import com.yingshi.server.repository.UploadTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * <p>
 * R2-D-6/7: 异常升级 — 单条失败用 logger.error 记录 (便于告警系统采集)，
 * 孤儿清理单条失败已在 UploadCleanupService.purgeQuarantined 内部按条隔离重试。
 * R2-D: dry-run 默认开启 (app.upload.orphan-scan.dry-run=true)，仅扫描统计不写库；
 * 确认无误后可关闭以启用真正的 quarantine 隔离与清理。
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

    @Value("${app.upload.orphan-scan.dry-run:true}")
    private boolean orphanScanDryRun;

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
                // R2-D-6/7: 异常升级为 error，便于告警系统 (如 webhook) 采集并触发告警。
                // TODO: 若后续接入告警 webhook (如飞书/Slack)，在此发送告警通知。
                logger.error("Failed to purge expired upload task taskId={} libraryId={}",
                        task.getId(), task.getLibraryId(), exception);
            }
        }
    }

    /**
     * FR-5 / R2-D: 每天 03:00 (Asia/Shanghai) 扫描 media 表中可能存在的孤儿对象并补偿。
     * 避开业务高峰，使用持久 cursor 分批扫描 (每批 5000 条)。
     * 先清理已过隔离期的 quarantine 条目，再执行新一轮扫描。
     */
    @Scheduled(cron = "${app.upload.orphan-scan.cron:0 0 3 * * *}", zone = "Asia/Shanghai")
    public void scanOrphanedUploadObjects() {
        try {
            // 先清理已过 7 天隔离期的孤儿对象 (走 PurgeIntent outbox 异步删除)
            int purged = uploadCleanupService.purgeQuarantined();
            if (purged > 0) {
                logger.info("R2-D purgeQuarantined: purged {} expired quarantine entries", purged);
            }
        } catch (Exception exception) {
            // R2-D-6/7: 异常升级为 error，便于告警系统采集。
            logger.error("R2-D purgeQuarantined failed", exception);
        }
        try {
            uploadCleanupService.scanOrphanedObjects(orphanScanDryRun);
        } catch (Exception exception) {
            // R2-D-6/7: 异常升级为 error，便于告警系统 (如 webhook) 采集并触发告警。
            // 孤儿扫描本身的单条失败已在 UploadCleanupService 内部按 media 隔离，不会中断整批扫描。
            logger.error("R2-D orphan object scan failed dryRun={}", orphanScanDryRun, exception);
        }
    }
}
