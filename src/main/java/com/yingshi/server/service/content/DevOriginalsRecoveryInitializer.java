package com.yingshi.server.service.content;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.file.FileSystemDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.service.auth.DevAuthSeedDataInitializer;
import com.yingshi.server.service.upload.LocalMediaStorageService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Date;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

@Configuration
@Profile("dev")
@ConditionalOnProperty(prefix = "yingshi.dev.recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DevOriginalsRecoveryInitializer {

    private static final Logger log = LoggerFactory.getLogger(DevOriginalsRecoveryInitializer.class);
    private static final String ORIGINALS_ROOT = "originals";
    private static final int DEFAULT_VIDEO_WIDTH = 1080;
    private static final int DEFAULT_VIDEO_HEIGHT = 1920;
    private static final long DEFAULT_VIDEO_DURATION_MILLIS = 15_000L;
    private static final long MIN_RECOVERABLE_FILE_SIZE_BYTES = 1024L;
    private static final int PREVIEW_MAX_DIMENSION = 1280;
    private static final int VIDEO_COVER_MAX_DIMENSION = 1280;

    @Bean
    @Order(5)
    ApplicationRunner originalsRecoveryRunner(
            MediaRepository mediaRepository,
            LocalMediaStorageService localMediaStorageService
    ) {
        return args -> recoverOriginals(
                DevAuthSeedDataInitializer.DEMO_LIBRARY_ID,
                mediaRepository,
                localMediaStorageService
        );
    }

    private void recoverOriginals(
            String libraryId,
            MediaRepository mediaRepository,
            LocalMediaStorageService localMediaStorageService
    ) {
        List<Path> mediaFiles = localMediaStorageService.listFilesRecursively(ORIGINALS_ROOT)
                .stream()
                .filter(this::isSupportedMediaFile)
                .toList();

        int restoredCount = 0;
        int existingCount = 0;
        int correctedCount = 0;
        int skippedCount = 0;
        for (Path path : mediaFiles) {
            MediaEntity media = toRecoveredMedia(libraryId, path, localMediaStorageService);
            if (media == null) {
                skippedCount++;
                continue;
            }
            Optional<MediaEntity> existingMedia = mediaRepository.findByIdAndLibraryId(media.getId(), libraryId);
            if (existingMedia.isPresent()) {
                existingCount++;
                MediaEntity existing = existingMedia.get();
                boolean changed = correctRecoveredTime(existing, media.getDisplayTimeMillis());
                changed = fillStorageFields(existing, localMediaStorageService) || changed;
                changed = ensureRecoveredPreview(existing, localMediaStorageService) || changed;
                if (changed) {
                    mediaRepository.save(existing);
                    correctedCount++;
                }
                continue;
            }
            ensureRecoveredPreview(media, localMediaStorageService);
            mediaRepository.save(media);
            restoredCount++;
        }

        if (!mediaFiles.isEmpty()) {
            log.info(
                    "Dev originals recovery scanned {} files, restored {}, existing {}, corrected {}, skipped {}.",
                    mediaFiles.size(),
                    restoredCount,
                    existingCount,
                    correctedCount,
                    skippedCount
            );
        }
    }

    private MediaEntity toRecoveredMedia(
            String libraryId,
            Path path,
            LocalMediaStorageService localMediaStorageService
    ) {
        String fileName = path.getFileName().toString();
        String mediaId = mediaIdFromFileName(fileName);
        if (mediaId == null) {
            return null;
        }

        long sizeBytes = fileSize(path);
        if (sizeBytes < MIN_RECOVERABLE_FILE_SIZE_BYTES) {
            return null;
        }

        String storagePath = localMediaStorageService.toRelativeStoragePath(path);
        MediaType mediaType = mediaTypeForPath(path);
        MediaDimensions dimensions = readDimensions(path, mediaType);
        if (dimensions == null) {
            return null;
        }

        String mediaUrl = "/api/media/files/" + mediaId;
        long displayTimeMillis = recoveredDisplayTimeMillis(storagePath, path);

        MediaEntity media = new MediaEntity();
        media.setId(mediaId);
        media.setLibraryId(libraryId);
        media.setMediaType(mediaType);
        media.setUrl(mediaUrl);
        media.setPreviewUrl(mediaType == MediaType.IMAGE ? mediaUrl + "?variant=preview" : mediaUrl);
        media.setOriginalUrl(mediaType == MediaType.IMAGE ? mediaUrl : null);
        media.setVideoUrl(mediaType == MediaType.VIDEO ? mediaUrl : null);
        media.setCoverUrl(null);
        media.setMimeType(mimeTypeForPath(path, mediaType));
        media.setSizeBytes(sizeBytes);
        media.setWidth(dimensions.width());
        media.setHeight(dimensions.height());
        media.setAspectRatio(((double) dimensions.width()) / dimensions.height());
        media.setDurationMillis(mediaType == MediaType.VIDEO ? DEFAULT_VIDEO_DURATION_MILLIS : null);
        media.setDisplayTimeMillis(displayTimeMillis);
        media.setCapturedAtMillis(displayTimeMillis);
        media.setImportedAtMillis(lastModifiedTimeMillis(path));
        media.setDisplayTimeSource("RECOVERED");
        media.setStoragePath(storagePath);
        media.setStorageProvider(localMediaStorageService.provider());
        media.setBucket(localMediaStorageService.bucket());
        media.setOriginalObjectKey(localMediaStorageService.originalObjectKey(storagePath));
        media.setChecksum(checksumForStoragePath(storagePath, localMediaStorageService));
        media.setSourceFingerprint("recovered:" + shortHash(storagePath));
        media.setDeletedAt(null);
        return media;
    }

    private boolean ensureRecoveredPreview(
            MediaEntity media,
            LocalMediaStorageService localMediaStorageService
    ) {
        if (media.getStoragePath() == null || media.getStoragePath().isBlank()) {
            return false;
        }
        String mediaUrl = "/api/media/files/" + media.getId();
        if (media.getMediaType() == MediaType.IMAGE) {
            boolean ready = localMediaStorageService.ensureImagePreview(media.getStoragePath(), media.getId(), PREVIEW_MAX_DIMENSION);
            String desiredPreviewUrl = ready ? mediaUrl + "?variant=preview" : mediaUrl;
            String desiredPreviewObjectKey = ready
                    ? localMediaStorageService.imagePreviewObjectKey(media.getStoragePath(), media.getId(), PREVIEW_MAX_DIMENSION)
                    : null;
            boolean changed = false;
            if (!desiredPreviewUrl.equals(media.getPreviewUrl())) {
                media.setPreviewUrl(desiredPreviewUrl);
                changed = true;
            }
            if (desiredPreviewObjectKey == null ? media.getPreviewObjectKey() != null : !desiredPreviewObjectKey.equals(media.getPreviewObjectKey())) {
                media.setPreviewObjectKey(desiredPreviewObjectKey);
                changed = true;
            }
            return changed;
        }
        if (media.getMediaType() == MediaType.VIDEO) {
            boolean ready = localMediaStorageService.ensureVideoCover(media.getStoragePath(), media.getId(), VIDEO_COVER_MAX_DIMENSION);
            String desiredCoverUrl = ready ? mediaUrl + "?variant=cover" : null;
            String desiredCoverObjectKey = ready
                    ? localMediaStorageService.videoCoverObjectKey(media.getStoragePath(), media.getId(), VIDEO_COVER_MAX_DIMENSION)
                    : null;
            boolean changed = false;
            if (desiredCoverUrl == null ? media.getCoverUrl() != null : !desiredCoverUrl.equals(media.getCoverUrl())) {
                media.setCoverUrl(desiredCoverUrl);
                changed = true;
            }
            if (desiredCoverObjectKey == null ? media.getCoverObjectKey() != null : !desiredCoverObjectKey.equals(media.getCoverObjectKey())) {
                media.setCoverObjectKey(desiredCoverObjectKey);
                changed = true;
            }
            return changed;
        }
        return false;
    }

    private boolean fillStorageFields(MediaEntity media, LocalMediaStorageService localMediaStorageService) {
        if (media.getStoragePath() == null || media.getStoragePath().isBlank()) {
            return false;
        }
        boolean changed = false;
        if (media.getStorageProvider() == null || media.getStorageProvider().isBlank()) {
            media.setStorageProvider(localMediaStorageService.provider());
            changed = true;
        }
        if (media.getBucket() == null || media.getBucket().isBlank()) {
            media.setBucket(localMediaStorageService.bucket());
            changed = true;
        }
        if (media.getOriginalObjectKey() == null || media.getOriginalObjectKey().isBlank()) {
            media.setOriginalObjectKey(localMediaStorageService.originalObjectKey(media.getStoragePath()));
            changed = true;
        }
        if (media.getChecksum() == null || media.getChecksum().isBlank()) {
            media.setChecksum(checksumForStoragePath(media.getStoragePath(), localMediaStorageService));
            changed = true;
        }
        return changed;
    }

    private String checksumForStoragePath(String storagePath, LocalMediaStorageService localMediaStorageService) {
        var metadata = localMediaStorageService.metadata(storagePath);
        return metadata == null ? null : metadata.checksum();
    }

    private String mediaIdFromFileName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return null;
        }
        String baseName = fileName.substring(0, dotIndex);
        if (!baseName.startsWith("media_")) {
            return null;
        }
        return baseName;
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

    private long recoveredDisplayTimeMillis(String storagePath, Path path) {
        Long metadataTimeMillis = metadataTimeMillis(path);
        if (metadataTimeMillis != null) {
            return metadataTimeMillis;
        }

        Long fileAttributeTimeMillis = fileAttributeTimeMillis(path);
        if (fileAttributeTimeMillis != null) {
            return fileAttributeTimeMillis;
        }

        String normalized = storagePath.replace('\\', '/');
        String[] parts = normalized.split("/");
        if (parts.length >= 4 && ORIGINALS_ROOT.equals(parts[0])) {
            Integer year = parsePositiveInt(parts[1]);
            Integer month = parsePositiveInt(parts[2]);
            if (year != null && month != null && month >= 1 && month <= 12) {
                return lastModifiedTimeMillis(path);
            }
        }
        return lastModifiedTimeMillis(path);
    }

    private boolean correctRecoveredTime(MediaEntity media, long recoveredDisplayTimeMillis) {
        if (!"RECOVERED".equals(media.getDisplayTimeSource())) {
            return false;
        }
        if (media.getDisplayTimeMillis() != null && media.getDisplayTimeMillis() == recoveredDisplayTimeMillis) {
            return false;
        }
        if (media.getDisplayTimeMillis() != null && !shouldReplaceRecoveredTime(media.getDisplayTimeMillis(), recoveredDisplayTimeMillis)) {
            return false;
        }
        media.setDisplayTimeMillis(recoveredDisplayTimeMillis);
        media.setCapturedAtMillis(recoveredDisplayTimeMillis);
        return true;
    }

    private boolean shouldReplaceRecoveredTime(long currentTimeMillis, long recoveredTimeMillis) {
        LocalDateTime current = LocalDateTime.ofInstant(Instant.ofEpochMilli(currentTimeMillis), ZoneId.systemDefault());
        LocalDateTime recovered = LocalDateTime.ofInstant(Instant.ofEpochMilli(recoveredTimeMillis), ZoneId.systemDefault());
        return current.getYear() == recovered.getYear()
                && current.getMonth() == recovered.getMonth()
                && current.getDayOfMonth() == 1
                && recovered.getDayOfMonth() != 1;
    }

    private Long metadataTimeMillis(Path path) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(path.toFile());
            List<Date> dates = new ArrayList<>();
            for (Directory directory : metadata.getDirectories()) {
                dates.addAll(metadataDates(directory));
            }
            return dates.stream()
                    .map(Date::toInstant)
                    .map(Instant::toEpochMilli)
                    .filter(this::isReasonableMediaTime)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
        } catch (ImageProcessingException | IOException exception) {
            return null;
        }
    }

    private List<Date> metadataDates(Directory directory) {
        List<Date> dates = new ArrayList<>();
        if (directory instanceof ExifSubIFDDirectory exif) {
            addDate(dates, exif, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
            addDate(dates, exif, ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED);
            return dates;
        }
        if (directory instanceof ExifIFD0Directory exif) {
            addDate(dates, exif, ExifIFD0Directory.TAG_DATETIME);
            return dates;
        }
        if (directory instanceof Mp4Directory mp4) {
            addDate(dates, mp4, Mp4Directory.TAG_CREATION_TIME);
            return dates;
        }
        if (directory instanceof QuickTimeDirectory quickTime) {
            addDate(dates, quickTime, QuickTimeDirectory.TAG_CREATION_TIME);
            return dates;
        }
        if (directory instanceof FileSystemDirectory fileSystem) {
            addDate(dates, fileSystem, FileSystemDirectory.TAG_FILE_MODIFIED_DATE);
            return dates;
        }
        return List.of();
    }

    private void addDate(List<Date> dates, Directory directory, int tagType) {
        Date date = nullableDate(directory, tagType);
        if (date != null) {
            dates.add(date);
        }
    }

    private Date nullableDate(Directory directory, int tagType) {
        try {
            return directory.getDate(tagType, TimeZone.getDefault());
        } catch (Exception exception) {
            return null;
        }
    }

    private Long fileAttributeTimeMillis(Path path) {
        try {
            Long creationTimeMillis = Files.readAttributes(path, "creationTime").get("creationTime") instanceof java.nio.file.attribute.FileTime fileTime
                    ? fileTime.toMillis()
                    : null;
            if (creationTimeMillis != null && isReasonableMediaTime(creationTimeMillis)) {
                return creationTimeMillis;
            }
        } catch (IOException | UnsupportedOperationException ignored) {
        }
        long lastModified = lastModifiedTimeMillis(path);
        return isReasonableMediaTime(lastModified) ? lastModified : null;
    }

    private boolean isReasonableMediaTime(long timeMillis) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMillis), ZoneId.systemDefault());
        int year = dateTime.getYear();
        return year >= 2000 && year <= LocalDateTime.now().getYear() + 1;
    }

    private Integer parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isSupportedMediaFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg")
                || fileName.endsWith(".png")
                || fileName.endsWith(".webp")
                || fileName.endsWith(".bmp")
                || fileName.endsWith(".gif")
                || fileName.endsWith(".mp4")
                || fileName.endsWith(".mov")
                || fileName.endsWith(".m4v")
                || fileName.endsWith(".webm")
                || fileName.endsWith(".3gp")
                || fileName.endsWith(".mkv")
                || fileName.endsWith(".avi");
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
        return "image/jpeg";
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return 0L;
        }
    }

    private long lastModifiedTimeMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return System.currentTimeMillis();
        }
    }

    private String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed).substring(0, 20);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 digest is not available.", exception);
        }
    }

    private record MediaDimensions(int width, int height) {
    }
}
