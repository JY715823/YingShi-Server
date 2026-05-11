package com.yingshi.server.service.content;

import com.yingshi.server.service.upload.LocalMediaStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

@Configuration
@Profile("dev")
@ConditionalOnProperty(prefix = "yingshi.dev.preview-cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DevPreviewCleanupInitializer {

    private static final Logger log = LoggerFactory.getLogger(DevPreviewCleanupInitializer.class);

    @Bean
    @Order(7)
    ApplicationRunner legacyPreviewCleanupRunner(LocalMediaStorageService localMediaStorageService) {
        return args -> {
            int deletedCount = localMediaStorageService.cleanupLegacyPreviewFiles();
            if (deletedCount > 0) {
                log.info("Deleted {} legacy local preview files.", deletedCount);
            }
        };
    }
}
