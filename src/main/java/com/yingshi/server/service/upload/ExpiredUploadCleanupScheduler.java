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
    private final UploadService uploadService;

    public ExpiredUploadCleanupScheduler(
            UploadTaskRepository uploadTaskRepository,
            UploadService uploadService
    ) {
        this.uploadTaskRepository = uploadTaskRepository;
        this.uploadService = uploadService;
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
                uploadService.purgeExpiredTask(task.getId());
            } catch (Exception exception) {
                logger.warn("Failed to purge expired upload task {}", task.getId(), exception);
            }
        }
    }
}
