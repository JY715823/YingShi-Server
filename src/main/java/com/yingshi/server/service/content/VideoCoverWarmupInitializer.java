package com.yingshi.server.service.content;

import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.service.upload.LocalMediaStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;

import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "yingshi.media.video-cover-warmup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VideoCoverWarmupInitializer {

    private static final Logger log = LoggerFactory.getLogger(VideoCoverWarmupInitializer.class);
    private static final int VIDEO_COVER_MAX_DIMENSION = 1280;

    @Bean
    @Order(20)
    ApplicationRunner videoCoverWarmupRunner(
            MediaRepository mediaRepository,
            LocalMediaStorageService localMediaStorageService,
            MediaStorageFieldService mediaStorageFieldService
    ) {
        return args -> warmMissingVideoCovers(
                mediaRepository,
                localMediaStorageService,
                mediaStorageFieldService
        );
    }

    private void warmMissingVideoCovers(
            MediaRepository mediaRepository,
            LocalMediaStorageService localMediaStorageService,
            MediaStorageFieldService mediaStorageFieldService
    ) {
        if (!localMediaStorageService.canGenerateVideoCovers()) {
            log.warn("Skipped video cover warmup because neither ffmpeg nor the Java video-frame fallback is available.");
            return;
        }

        List<MediaEntity> videos = mediaRepository.findTop200RecentByMediaType(MediaType.VIDEO, PageRequest.of(0, 200));
        int warmedCount = 0;
        int skippedCount = 0;
        for (MediaEntity media : videos) {
            String storagePath = mediaStorageFieldService.storagePathForRead(media);
            if (storagePath == null || storagePath.isBlank()) {
                skippedCount++;
                continue;
            }
            boolean ready = localMediaStorageService.ensureVideoCover(
                    storagePath,
                    media.getId(),
                    VIDEO_COVER_MAX_DIMENSION
            );
            if (!ready) {
                skippedCount++;
                continue;
            }
            String mediaUrl = "/api/media/files/" + media.getId();
            String coverObjectKey = localMediaStorageService.videoCoverObjectKey(
                    storagePath,
                    media.getId(),
                    VIDEO_COVER_MAX_DIMENSION
            );
            boolean changed = false;
            changed = setIfChanged(media.getCoverUrl(), mediaUrl + "?variant=cover", media::setCoverUrl) || changed;
            changed = setIfChanged(media.getPreviewUrl(), mediaUrl + "?variant=cover", media::setPreviewUrl) || changed;
            changed = setIfChanged(media.getCoverObjectKey(), coverObjectKey, media::setCoverObjectKey) || changed;
            changed = setIfChanged(media.getPreviewObjectKey(), coverObjectKey, media::setPreviewObjectKey) || changed;
            if (changed) {
                mediaRepository.save(media);
            }
            warmedCount++;
        }
        if (!videos.isEmpty()) {
            log.info("Video cover warmup scanned {} videos, warmed {}, skipped {}.", videos.size(), warmedCount, skippedCount);
        }
    }

    private boolean setIfChanged(String current, String desired, java.util.function.Consumer<String> setter) {
        if (desired.equals(current)) {
            return false;
        }
        setter.accept(desired);
        return true;
    }
}
