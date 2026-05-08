package com.yingshi.server.service.content;

import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.service.auth.DevAuthSeedDataInitializer;
import com.yingshi.server.service.upload.LocalMediaStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

@Configuration
@Profile("dev")
@ConditionalOnProperty(prefix = "yingshi.dev.local-media-recovery", name = "enabled", havingValue = "true")
public class DevLocalMediaRecoveryInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DevLocalMediaRecoveryInitializer.class);
    private static final int DEFAULT_VIDEO_WIDTH = 1080;
    private static final int DEFAULT_VIDEO_HEIGHT = 1920;

    @Bean
    @Order(4)
    ApplicationRunner localMediaRecoveryRunner(
            MediaRepository mediaRepository,
            LocalMediaStorageService localMediaStorageService
    ) {
        return args -> {
            int recoveredCount = 0;
            for (Path path : localMediaStorageService.listFilesRecursively("originals")) {
                if (!isSupportedMediaFile(path)) {
                    continue;
                }
                String mediaId = mediaIdFor(path);
                if (mediaRepository.findById(mediaId).isPresent()) {
                    continue;
                }
                MediaEntity media = createRecoveredMedia(
                        DevAuthSeedDataInitializer.DEMO_LIBRARY_ID,
                        mediaId,
                        path,
                        localMediaStorageService
                );
                if (media == null) {
                    continue;
                }
                mediaRepository.save(media);
                recoveredCount++;
            }
            if (recoveredCount > 0) {
                logger.info("Recovered {} local media records from local-storage/originals.", recoveredCount);
            }
        };
    }

    private MediaEntity createRecoveredMedia(
            String libraryId,
            String mediaId,
            Path path,
            LocalMediaStorageService localMediaStorageService
    ) {
        MediaType mediaType = mediaTypeForPath(path);
        MediaDimensions dimensions = readDimensions(path, mediaType);
        if (dimensions == null) {
            return null;
        }

        long displayTimeMillis = lastModifiedTimeMillis(path);
        long sizeBytes = fileSize(path);
        String storagePath = localMediaStorageService.toRelativeStoragePath(path);
        String mediaUrl = "/api/media/files/" + mediaId;

        MediaEntity media = new MediaEntity();
        media.setId(mediaId);
        media.setLibraryId(libraryId);
        media.setMediaType(mediaType);
        media.setUrl(mediaUrl);
        media.setPreviewUrl(mediaUrl);
        media.setOriginalUrl(mediaType == MediaType.IMAGE ? mediaUrl : null);
        media.setVideoUrl(mediaType == MediaType.VIDEO ? mediaUrl : null);
        media.setCoverUrl(mediaType == MediaType.VIDEO ? mediaUrl : null);
        media.setMimeType(mimeTypeForPath(path, mediaType));
        media.setSizeBytes(sizeBytes);
        media.setWidth(dimensions.width());
        media.setHeight(dimensions.height());
        media.setAspectRatio(((double) dimensions.width()) / dimensions.height());
        media.setDurationMillis(null);
        media.setDisplayTimeMillis(displayTimeMillis);
        media.setCapturedAtMillis(displayTimeMillis);
        media.setImportedAtMillis(displayTimeMillis);
        media.setDisplayTimeSource("IMPORTED");
        media.setStoragePath(storagePath);
        media.setDeletedAt(null);
        return media;
    }

    private boolean isSupportedMediaFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg")
                || fileName.endsWith(".png")
                || fileName.endsWith(".webp")
                || fileName.endsWith(".bmp")
                || fileName.endsWith(".gif")
                || fileName.endsWith(".heic")
                || fileName.endsWith(".heif")
                || fileName.endsWith(".avif")
                || fileName.endsWith(".mp4")
                || fileName.endsWith(".mov")
                || fileName.endsWith(".m4v")
                || fileName.endsWith(".webm")
                || fileName.endsWith(".3gp")
                || fileName.endsWith(".mkv")
                || fileName.endsWith(".avi");
    }

    private String mediaIdFor(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private MediaType mediaTypeForPath(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".mp4")
                || fileName.endsWith(".mov")
                || fileName.endsWith(".m4v")
                || fileName.endsWith(".webm")
                || fileName.endsWith(".3gp")
                || fileName.endsWith(".mkv")
                || fileName.endsWith(".avi")) {
            return MediaType.VIDEO;
        }
        return MediaType.IMAGE;
    }

    private MediaDimensions readDimensions(Path path, MediaType mediaType) {
        if (mediaType == MediaType.VIDEO) {
            return new MediaDimensions(DEFAULT_VIDEO_WIDTH, DEFAULT_VIDEO_HEIGHT);
        }
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
                return new MediaDimensions(image.getWidth(), image.getHeight());
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private String mimeTypeForPath(Path path, MediaType mediaType) {
        try {
            String probed = Files.probeContentType(path);
            if (probed != null && !probed.isBlank()) {
                return probed;
            }
        } catch (IOException ignored) {
        }

        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (mediaType == MediaType.VIDEO) {
            if (fileName.endsWith(".mov")) {
                return "video/quicktime";
            }
            if (fileName.endsWith(".webm")) {
                return "video/webm";
            }
            if (fileName.endsWith(".3gp")) {
                return "video/3gpp";
            }
            if (fileName.endsWith(".mkv")) {
                return "video/x-matroska";
            }
            if (fileName.endsWith(".avi")) {
                return "video/x-msvideo";
            }
            return "video/mp4";
        }

        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".webp")) {
            return "image/webp";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileName.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (fileName.endsWith(".heic")) {
            return "image/heic";
        }
        if (fileName.endsWith(".heif")) {
            return "image/heif";
        }
        if (fileName.endsWith(".avif")) {
            return "image/avif";
        }
        return "image/jpeg";
    }

    private long lastModifiedTimeMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return Instant.now().toEpochMilli();
        }
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return 0L;
        }
    }

    private record MediaDimensions(int width, int height) {
    }
}
