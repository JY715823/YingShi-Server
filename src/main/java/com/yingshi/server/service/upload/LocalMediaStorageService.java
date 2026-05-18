package com.yingshi.server.service.upload;

import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.config.StorageProperties;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.service.storage.ObjectMetadata;
import com.yingshi.server.service.storage.ObjectKeyPolicy;
import com.yingshi.server.service.storage.ObjectStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;

@Service
public class LocalMediaStorageService {

    private static final DateTimeFormatter STORAGE_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final int JPEG_PREVIEW_QUALITY_PERCENT = 90;
    private static final long VIDEO_COVER_TIMEOUT_SECONDS = 20L;
    private static final Pattern LEGACY_PREVIEW_FILE_NAME = Pattern.compile("media_[A-Za-z0-9_-]+-\\d+\\.jpg");
    private static final String LOCAL_PROVIDER = "local";

    private final Path rootPath;
    private final ObjectStorageService objectStorageService;

    public LocalMediaStorageService(StorageProperties storageProperties, ObjectStorageService objectStorageService) {
        this.rootPath = Paths.get(storageProperties.localRoot()).toAbsolutePath().normalize();
        this.objectStorageService = objectStorageService;
    }

    public StoredFile storeOriginal(
            String mediaId,
            long displayTimeMillis,
            MediaType mediaType,
            String originalFileName,
            MultipartFile file
    ) {
        String extension = resolveExtension(originalFileName, mediaType);
        Path directory = rootPath.resolve("originals").resolve(monthBucket(displayTimeMillis)).normalize();
        Path target = directory.resolve(mediaId + extension).normalize();
        String objectKey = toRelativeStoragePath(target);
        try {
            ObjectMetadata metadata;
            try (InputStream inputStream = file.getInputStream()) {
                metadata = objectStorageService.put(objectKey, file.getContentType(), file.getSize(), inputStream);
            }
            return new StoredFile(
                    objectKey,
                    target.getFileName().toString(),
                    objectStorageService.provider(),
                    objectStorageService.bucket(),
                    metadata.objectKey(),
                    metadata.checksum(),
                    metadata.sizeBytes()
            );
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to store uploaded file.");
        }
    }

    public Resource load(String storagePath) {
        if (isRelativeStoragePath(storagePath)) {
            return objectStorageService.get(toObjectKey(storagePath)).resource();
        }
        if (ObjectKeyPolicy.looksLikeFullUrl(storagePath)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Stored media file was not found.");
        }
        Path path = resolveStoragePath(storagePath);
        if (!Files.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Stored media file was not found.");
        }
        return new FileSystemResource(path);
    }

    public Resource loadRange(String storagePath, long start, long endInclusive) {
        if (isRelativeStoragePath(storagePath)) {
            return objectStorageService.getRange(toObjectKey(storagePath), start, endInclusive).resource();
        }
        if (ObjectKeyPolicy.looksLikeFullUrl(storagePath)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Stored media file was not found.");
        }
        Path path = resolveStoragePath(storagePath);
        if (!Files.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Stored media file was not found.");
        }
        return new FileSystemResource(path);
    }

    public Resource loadObject(String objectKey) {
        return objectStorageService.get(ObjectKeyPolicy.normalizeRelativeObjectKey(objectKey)).resource();
    }

    public Resource loadPreview(String storagePath, String cacheKey, int maxDimension) {
        if (!ensureImagePreview(storagePath, cacheKey, maxDimension)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Preview could not be generated for this media.");
        }
        if (isLocalProvider()) {
            Path sourcePath = resolveStoragePath(storagePath);
            return new FileSystemResource(imagePreviewPath(sourcePath, cacheKey, maxDimension));
        }
        return objectStorageService.get(imagePreviewObjectKey(storagePath, cacheKey, maxDimension)).resource();
    }

    public boolean ensureImagePreview(String storagePath, String cacheKey, int maxDimension) {
        if (!isLocalProvider()) {
            return ensureRemoteImagePreview(storagePath, cacheKey, maxDimension);
        }
        Path sourcePath = resolveStoragePath(storagePath);
        if (!Files.exists(sourcePath)) {
            return false;
        }
        Path previewPath = imagePreviewPath(sourcePath, cacheKey, maxDimension);
        try {
            if (!shouldRegeneratePreview(sourcePath, previewPath)) {
                return true;
            }
            Files.createDirectories(previewPath.getParent());
            BufferedImage sourceImage = ImageIO.read(sourcePath.toFile());
            if (sourceImage == null || sourceImage.getWidth() <= 0 || sourceImage.getHeight() <= 0) {
                return false;
            }
            BufferedImage previewImage = resizeImage(applyExifOrientation(sourcePath, sourceImage), maxDimension);
            writeJpeg(previewImage, previewPath, JPEG_PREVIEW_QUALITY_PERCENT / 100f);
            return Files.exists(previewPath) && Files.size(previewPath) > 0L;
        } catch (IOException exception) {
            return false;
        }
    }

    public Resource loadVideoCover(String storagePath, String cacheKey, int maxDimension) {
        if (!ensureVideoCover(storagePath, cacheKey, maxDimension)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Video cover could not be generated for this media.");
        }
        if (isLocalProvider()) {
            Path sourcePath = resolveStoragePath(storagePath);
            return new FileSystemResource(videoCoverPath(sourcePath, cacheKey, maxDimension));
        }
        return objectStorageService.get(videoCoverObjectKey(storagePath, cacheKey, maxDimension)).resource();
    }

    public String provider() {
        return objectStorageService.provider();
    }

    public String bucket() {
        return objectStorageService.bucket();
    }

    public String originalObjectKey(String storagePath) {
        return ObjectKeyPolicy.tryNormalizeRelativeObjectKey(storagePath);
    }

    public String imagePreviewObjectKey(String storagePath, String cacheKey, int maxDimension) {
        return previewDirectoryObjectKey(storagePath)
                + "/"
                + cacheKey
                + "-preview-v2-"
                + maxDimension
                + ".jpg";
    }

    public String videoCoverObjectKey(String storagePath, String cacheKey, int maxDimension) {
        return previewDirectoryObjectKey(storagePath)
                + "/"
                + cacheKey
                + "-cover-v1-"
                + maxDimension
                + ".jpg";
    }

    public ObjectMetadata metadata(String storagePath) {
        if (!isRelativeStoragePath(storagePath)) {
            return null;
        }
        return objectStorageService.getMetadata(toObjectKey(storagePath)).orElse(null);
    }

    public ObjectMetadata metadataForObjectKey(String objectKey) {
        String normalizedObjectKey = ObjectKeyPolicy.tryNormalizeRelativeObjectKey(objectKey);
        if (normalizedObjectKey == null) {
            return null;
        }
        return objectStorageService.getMetadata(normalizedObjectKey).orElse(null);
    }

    public ObjectMetadata metadataForStoragePath(String storagePath) {
        if (isRelativeStoragePath(storagePath)) {
            return metadataForObjectKey(storagePath);
        }
        if (ObjectKeyPolicy.looksLikeFullUrl(storagePath)) {
            return null;
        }
        Path path = resolveStoragePath(storagePath);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        return fastFileMetadata(storagePath, path);
    }

    public boolean objectExists(String objectKey) {
        String normalizedObjectKey = ObjectKeyPolicy.tryNormalizeRelativeObjectKey(objectKey);
        return normalizedObjectKey != null && objectStorageService.exists(normalizedObjectKey);
    }

    public boolean ensureVideoCover(String storagePath, String cacheKey, int maxDimension) {
        if (!isLocalProvider()) {
            return ensureRemoteVideoCover(storagePath, cacheKey, maxDimension);
        }
        Path sourcePath = resolveStoragePath(storagePath);
        if (!Files.exists(sourcePath)) {
            return false;
        }
        Path coverPath = videoCoverPath(sourcePath, cacheKey, maxDimension);
        try {
            if (!shouldRegeneratePreview(sourcePath, coverPath)) {
                return true;
            }
            Files.createDirectories(coverPath.getParent());
            Path tempFrame = Files.createTempFile(coverPath.getParent(), cacheKey + "-cover-frame-", ".jpg");
            try {
                if (!extractVideoFrame(sourcePath, tempFrame, "1") && !extractVideoFrame(sourcePath, tempFrame, "0")) {
                    return false;
                }
                BufferedImage frameImage = ImageIO.read(tempFrame.toFile());
                if (frameImage == null || frameImage.getWidth() <= 0 || frameImage.getHeight() <= 0) {
                    return false;
                }
                BufferedImage coverImage = resizeImage(frameImage, maxDimension);
                writeJpeg(coverImage, coverPath, JPEG_PREVIEW_QUALITY_PERCENT / 100f);
                return Files.exists(coverPath) && Files.size(coverPath) > 0L;
            } finally {
                Files.deleteIfExists(tempFrame);
            }
        } catch (IOException exception) {
            return false;
        }
    }

    public String ensureSeedImage(String seedName, int sourceOffset) {
        Path seedDirectory = rootPath.resolve("seed").normalize();
        Path target = seedDirectory.resolve(seedName + ".jpg").normalize();
        try {
            Files.createDirectories(seedDirectory);
            if (Files.exists(target) && Files.isRegularFile(target) && isReadableImageFile(target)) {
                return rootPath.relativize(target).toString().replace(FileSystems.getDefault().getSeparator(), "/");
            }
            List<Path> sourceImages = findSeedSourceImages();
            if (!sourceImages.isEmpty()) {
                Path source = sourceImages.get(Math.floorMod(seedName.hashCode() + sourceOffset, sourceImages.size()));
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                writeGeneratedSeedImage(seedName, sourceOffset, target);
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to prepare seeded media file.");
        }
        return rootPath.relativize(target).toString().replace(FileSystems.getDefault().getSeparator(), "/");
    }

    public String ensureSeedVideo(String seedName, long sizeBytes) {
        Path seedDirectory = rootPath.resolve("seed").normalize();
        Path target = seedDirectory.resolve(seedName + ".mp4").normalize();
        long normalizedSizeBytes = Math.max(sizeBytes, 1024L);
        try {
            Files.createDirectories(seedDirectory);
            if (Files.isRegularFile(target) && Files.size(target) >= normalizedSizeBytes) {
                return rootPath.relativize(target).toString().replace(FileSystems.getDefault().getSeparator(), "/");
            }
            writeGeneratedSeedVideo(target, normalizedSizeBytes);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to prepare seeded video file.");
        }
        return rootPath.relativize(target).toString().replace(FileSystems.getDefault().getSeparator(), "/");
    }

    public List<Path> listFilesRecursively(String relativeDirectory) {
        if (!isLocalProvider()) {
            return List.of();
        }
        Path directory = rootPath.resolve(relativeDirectory).normalize();
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to scan local media directory.");
        }
    }

    public String toRelativeStoragePath(Path absolutePath) {
        Path normalizedPath = absolutePath.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(rootPath)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_STORAGE_ERROR, "Media path is outside of the configured local storage root.");
        }
        return rootPath.relativize(normalizedPath)
                .toString()
                .replace(FileSystems.getDefault().getSeparator(), "/");
    }

    public int cleanupLegacyPreviewFiles() {
        if (!isLocalProvider()) {
            return 0;
        }
        Path previewRoot = rootPath.resolve("previews").normalize();
        if (!Files.exists(previewRoot)) {
            return 0;
        }
        try (Stream<Path> stream = Files.walk(previewRoot)) {
            List<Path> legacyPreviewFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isLegacyPreviewFile)
                    .toList();
            int deletedCount = 0;
            for (Path path : legacyPreviewFiles) {
                try {
                    Files.deleteIfExists(path);
                    deletedCount++;
                } catch (IOException ignored) {
                }
            }
            return deletedCount;
        } catch (IOException exception) {
            return 0;
        }
    }

    public List<String> deleteStoredMediaFiles(String storagePath, String cacheKey) {
        if (!isLocalProvider()) {
            return deleteRemoteStoredMediaFiles(storagePath, cacheKey);
        }
        Set<Path> candidates = new LinkedHashSet<>();
        Path sourcePath = resolveStoragePath(storagePath);
        if (!sourcePath.startsWith(rootPath)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_STORAGE_ERROR, "Media path is outside of the configured local storage root.");
        }
        candidates.add(sourcePath);

        Path previewRoot = rootPath.resolve("previews").normalize();
        if (Files.exists(previewRoot)) {
            try (Stream<Path> stream = Files.walk(previewRoot)) {
                stream
                        .filter(Files::isRegularFile)
                        .filter(path -> isDerivedFileForCacheKey(path, cacheKey))
                        .forEach(candidates::add);
            } catch (IOException exception) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to scan media preview files for deletion.");
            }
        }

        List<String> deletedFiles = new java.util.ArrayList<>();
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (!normalized.startsWith(rootPath)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_STORAGE_ERROR, "Refusing to delete media file outside local storage.");
            }
            if (!Files.exists(normalized)) {
                continue;
            }
            if (!Files.isRegularFile(normalized)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_STORAGE_ERROR, "Refusing to delete non-file media path.");
            }
            try {
                if (normalized.startsWith(rootPath)) {
                    objectStorageService.delete(toRelativeStoragePath(normalized));
                } else {
                    Files.delete(normalized);
                }
                deletedFiles.add(toRelativeStoragePath(normalized));
            } catch (IOException exception) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to delete stored media file.");
            }
        }
        return deletedFiles;
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String resolveExtension(String fileName, MediaType mediaType) {
        String sanitizedName = sanitizeFileName(fileName == null ? "" : fileName);
        int dotIndex = sanitizedName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < sanitizedName.length() - 1) {
            return sanitizedName.substring(dotIndex).toLowerCase(Locale.ROOT);
        }
        return mediaType == MediaType.VIDEO ? ".mp4" : ".jpg";
    }

    private String monthBucket(long displayTimeMillis) {
        return Instant.ofEpochMilli(displayTimeMillis)
                .atZone(ZoneId.systemDefault())
                .format(STORAGE_MONTH_FORMATTER);
    }

    private Path previewDirectoryFor(Path sourcePath) {
        Path relative = rootPath.relativize(sourcePath.toAbsolutePath().normalize());
        if (relative.getNameCount() >= 3 && "originals".equals(relative.getName(0).toString())) {
            return rootPath.resolve("previews")
                    .resolve(relative.getName(1).toString())
                    .resolve(relative.getName(2).toString())
                    .normalize();
        }
        return rootPath.resolve("previews").normalize();
    }

    private Path imagePreviewPath(Path sourcePath, String cacheKey, int maxDimension) {
        return previewDirectoryFor(sourcePath)
                .resolve(cacheKey + "-preview-v2-" + maxDimension + ".jpg")
                .normalize();
    }

    private Path videoCoverPath(Path sourcePath, String cacheKey, int maxDimension) {
        return previewDirectoryFor(sourcePath)
                .resolve(cacheKey + "-cover-v1-" + maxDimension + ".jpg")
                .normalize();
    }

    private boolean isLegacyPreviewFile(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        Path previewRoot = rootPath.resolve("previews").normalize();
        if (!normalized.startsWith(previewRoot)) {
            return false;
        }
        String fileName = normalized.getFileName().toString();
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (lowerName.contains("-preview-v2-") || lowerName.contains("-cover-v1-")) {
            return false;
        }
        return LEGACY_PREVIEW_FILE_NAME.matcher(fileName).matches();
    }

    private boolean isDerivedFileForCacheKey(Path path, String cacheKey) {
        Path normalized = path.toAbsolutePath().normalize();
        Path previewRoot = rootPath.resolve("previews").normalize();
        if (!normalized.startsWith(previewRoot)) {
            return false;
        }
        String fileName = normalized.getFileName().toString();
        return fileName.startsWith(cacheKey + "-preview-v2-") || fileName.startsWith(cacheKey + "-cover-v1-");
    }

    private Path resolveStoragePath(String storagePath) {
        Path rawPath = Paths.get(storagePath);
        if (rawPath.isAbsolute()) {
            return rawPath.toAbsolutePath().normalize();
        }
        return rootPath.resolve(rawPath).toAbsolutePath().normalize();
    }

    private boolean isLocalProvider() {
        return LOCAL_PROVIDER.equalsIgnoreCase(objectStorageService.provider());
    }

    private boolean isRelativeStoragePath(String storagePath) {
        return ObjectKeyPolicy.isRelativeObjectKey(storagePath);
    }

    private String toObjectKey(String storagePath) {
        return ObjectKeyPolicy.normalizeRelativeObjectKey(storagePath);
    }

    private ObjectMetadata fastFileMetadata(String storagePath, Path path) {
        try {
            return new ObjectMetadata(
                    storagePath,
                    Files.probeContentType(path),
                    Files.size(path),
                    null,
                    Files.getLastModifiedTime(path).toMillis()
            );
        } catch (IOException exception) {
            return new ObjectMetadata(storagePath, null, null, null, null);
        }
    }

    private String previewDirectoryObjectKey(String storagePath) {
        String objectKey = originalObjectKey(storagePath);
        if (objectKey != null) {
            String normalized = objectKey.replace('\\', '/');
            String[] parts = normalized.split("/");
            if (parts.length >= 4 && "originals".equals(parts[0])) {
                return "previews/" + parts[1] + "/" + parts[2];
            }
        }
        return "previews";
    }

    private boolean ensureRemoteImagePreview(String storagePath, String cacheKey, int maxDimension) {
        String sourceObjectKey = originalObjectKey(storagePath);
        String previewObjectKey = imagePreviewObjectKey(storagePath, cacheKey, maxDimension);
        if (sourceObjectKey == null || !objectStorageService.exists(sourceObjectKey)) {
            return false;
        }
        ObjectMetadata sourceMetadata = objectStorageService.getMetadata(sourceObjectKey).orElse(null);
        ObjectMetadata previewMetadata = objectStorageService.getMetadata(previewObjectKey).orElse(null);
        if (!shouldRegenerateObject(sourceMetadata, previewMetadata)) {
            return true;
        }
        Path sourcePath = null;
        Path previewPath = null;
        try {
            sourcePath = copyObjectToTempFile(sourceObjectKey, cacheKey + "-source-", extensionForObjectKey(sourceObjectKey));
            previewPath = Files.createTempFile(cacheKey + "-preview-", ".jpg");
            BufferedImage sourceImage = ImageIO.read(sourcePath.toFile());
            if (sourceImage == null || sourceImage.getWidth() <= 0 || sourceImage.getHeight() <= 0) {
                return false;
            }
            BufferedImage previewImage = resizeImage(applyExifOrientation(sourcePath, sourceImage), maxDimension);
            writeJpeg(previewImage, previewPath, JPEG_PREVIEW_QUALITY_PERCENT / 100f);
            putGeneratedObject(previewObjectKey, previewPath, "image/jpeg");
            return objectStorageService.exists(previewObjectKey);
        } catch (IOException exception) {
            return false;
        } finally {
            deleteTempFile(sourcePath);
            deleteTempFile(previewPath);
        }
    }

    private boolean ensureRemoteVideoCover(String storagePath, String cacheKey, int maxDimension) {
        String sourceObjectKey = originalObjectKey(storagePath);
        String coverObjectKey = videoCoverObjectKey(storagePath, cacheKey, maxDimension);
        if (sourceObjectKey == null || !objectStorageService.exists(sourceObjectKey)) {
            return false;
        }
        ObjectMetadata sourceMetadata = objectStorageService.getMetadata(sourceObjectKey).orElse(null);
        ObjectMetadata coverMetadata = objectStorageService.getMetadata(coverObjectKey).orElse(null);
        if (!shouldRegenerateObject(sourceMetadata, coverMetadata)) {
            return true;
        }
        Path sourcePath = null;
        Path coverPath = null;
        Path tempFrame = null;
        try {
            sourcePath = copyObjectToTempFile(sourceObjectKey, cacheKey + "-video-source-", extensionForObjectKey(sourceObjectKey));
            coverPath = Files.createTempFile(cacheKey + "-cover-", ".jpg");
            tempFrame = Files.createTempFile(cacheKey + "-cover-frame-", ".jpg");
            if (!extractVideoFrame(sourcePath, tempFrame, "1") && !extractVideoFrame(sourcePath, tempFrame, "0")) {
                return false;
            }
            BufferedImage frameImage = ImageIO.read(tempFrame.toFile());
            if (frameImage == null || frameImage.getWidth() <= 0 || frameImage.getHeight() <= 0) {
                return false;
            }
            BufferedImage coverImage = resizeImage(frameImage, maxDimension);
            writeJpeg(coverImage, coverPath, JPEG_PREVIEW_QUALITY_PERCENT / 100f);
            putGeneratedObject(coverObjectKey, coverPath, "image/jpeg");
            return objectStorageService.exists(coverObjectKey);
        } catch (IOException exception) {
            return false;
        } finally {
            deleteTempFile(sourcePath);
            deleteTempFile(coverPath);
            deleteTempFile(tempFrame);
        }
    }

    private Path copyObjectToTempFile(String objectKey, String prefix, String suffix) throws IOException {
        Path tempFile = Files.createTempFile(prefix, suffix);
        try (InputStream inputStream = objectStorageService.get(objectKey).resource().getInputStream();
             OutputStream outputStream = Files.newOutputStream(tempFile)) {
            inputStream.transferTo(outputStream);
        }
        ObjectMetadata metadata = objectStorageService.getMetadata(objectKey).orElse(null);
        if (metadata != null && metadata.lastModifiedMillis() != null) {
            Files.setLastModifiedTime(tempFile, FileTime.fromMillis(metadata.lastModifiedMillis()));
        }
        return tempFile;
    }

    private void putGeneratedObject(String objectKey, Path path, String contentType) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            objectStorageService.put(objectKey, contentType, Files.size(path), inputStream);
        }
    }

    private boolean shouldRegenerateObject(ObjectMetadata sourceMetadata, ObjectMetadata generatedMetadata) {
        if (generatedMetadata == null || generatedMetadata.sizeBytes() == null || generatedMetadata.sizeBytes() <= 0L) {
            return true;
        }
        if (sourceMetadata == null || sourceMetadata.lastModifiedMillis() == null || generatedMetadata.lastModifiedMillis() == null) {
            return false;
        }
        return generatedMetadata.lastModifiedMillis() < sourceMetadata.lastModifiedMillis();
    }

    private List<String> deleteRemoteStoredMediaFiles(String storagePath, String cacheKey) {
        String sourceObjectKey = originalObjectKey(storagePath);
        if (sourceObjectKey == null) {
            return List.of();
        }
        List<String> deletedKeys = new java.util.ArrayList<>();
        for (String objectKey : List.of(
                sourceObjectKey,
                imagePreviewObjectKey(storagePath, cacheKey, 1280),
                videoCoverObjectKey(storagePath, cacheKey, 1280)
        )) {
            if (objectStorageService.delete(objectKey)) {
                deletedKeys.add(objectKey);
            }
        }
        return deletedKeys;
    }

    private String extensionForObjectKey(String objectKey) {
        int slashIndex = objectKey.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? objectKey.substring(slashIndex + 1) : objectKey;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex);
        }
        return ".tmp";
    }

    private void deleteTempFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private boolean shouldRegeneratePreview(Path sourcePath, Path previewPath) throws IOException {
        if (!Files.exists(previewPath)) {
            return true;
        }
        return Files.getLastModifiedTime(previewPath).toMillis() < Files.getLastModifiedTime(sourcePath).toMillis();
    }

    private BufferedImage resizeImage(BufferedImage sourceImage, int maxDimension) {
        int sourceWidth = sourceImage.getWidth();
        int sourceHeight = sourceImage.getHeight();
        int longestEdge = Math.max(sourceWidth, sourceHeight);
        if (longestEdge <= maxDimension) {
            BufferedImage copied = rgbImage(sourceWidth, sourceHeight);
            Graphics2D graphics = copied.createGraphics();
            configureHighQualityRendering(graphics);
            graphics.drawImage(sourceImage, 0, 0, null);
            graphics.dispose();
            return copied;
        }

        double scale = maxDimension / (double) longestEdge;
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        BufferedImage resized = rgbImage(targetWidth, targetHeight);
        Graphics2D graphics = resized.createGraphics();
        configureHighQualityRendering(graphics);
        graphics.drawImage(sourceImage, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return resized;
    }

    private BufferedImage rgbImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    private void configureHighQualityRendering(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    }

    private BufferedImage applyExifOrientation(Path sourcePath, BufferedImage image) {
        int orientation = readExifOrientation(sourcePath);
        return switch (orientation) {
            case 3 -> rotate180(image);
            case 6 -> rotate90Clockwise(image);
            case 8 -> rotate90CounterClockwise(image);
            default -> image;
        };
    }

    private int readExifOrientation(Path sourcePath) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(sourcePath.toFile());
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            return directory == null ? 1 : directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
        } catch (Exception exception) {
            return 1;
        }
    }

    private BufferedImage rotate180(BufferedImage image) {
        BufferedImage rotated = rgbImage(image.getWidth(), image.getHeight());
        Graphics2D graphics = rotated.createGraphics();
        configureHighQualityRendering(graphics);
        graphics.rotate(Math.PI, image.getWidth() / 2.0, image.getHeight() / 2.0);
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return rotated;
    }

    private BufferedImage rotate90Clockwise(BufferedImage image) {
        BufferedImage rotated = rgbImage(image.getHeight(), image.getWidth());
        Graphics2D graphics = rotated.createGraphics();
        configureHighQualityRendering(graphics);
        graphics.translate(image.getHeight(), 0);
        graphics.rotate(Math.PI / 2);
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return rotated;
    }

    private BufferedImage rotate90CounterClockwise(BufferedImage image) {
        BufferedImage rotated = rgbImage(image.getHeight(), image.getWidth());
        Graphics2D graphics = rotated.createGraphics();
        configureHighQualityRendering(graphics);
        graphics.translate(0, image.getWidth());
        graphics.rotate(-Math.PI / 2);
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return rotated;
    }

    private void writeJpeg(BufferedImage image, Path targetPath, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ImageIO.write(image, "jpg", targetPath.toFile());
            return;
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream outputStream = ImageIO.createImageOutputStream(targetPath.toFile())) {
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(Math.max(0.1f, Math.min(1f, quality)));
            }
            writer.setOutput(outputStream);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
    }

    private void writeGeneratedSeedImage(String seedName, int sourceOffset, Path target) throws IOException {
        int width = 1280;
        int height = 960;
        int baseHue = Math.floorMod(seedName.hashCode() + sourceOffset * 41, 360);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            configureHighQualityRendering(graphics);
            Color start = Color.getHSBColor(baseHue / 360f, 0.42f, 0.86f);
            Color end = Color.getHSBColor(((baseHue + 46) % 360) / 360f, 0.34f, 0.58f);
            for (int y = 0; y < height; y++) {
                float ratio = y / (float) Math.max(1, height - 1);
                int red = Math.round(start.getRed() * (1 - ratio) + end.getRed() * ratio);
                int green = Math.round(start.getGreen() * (1 - ratio) + end.getGreen() * ratio);
                int blue = Math.round(start.getBlue() * (1 - ratio) + end.getBlue() * ratio);
                graphics.setColor(new Color(red, green, blue));
                graphics.drawLine(0, y, width, y);
            }
            graphics.setColor(new Color(255, 255, 255, 78));
            int stripeWidth = 120;
            for (int x = -width; x < width * 2; x += stripeWidth * 2) {
                graphics.fillPolygon(
                        new int[]{x, x + stripeWidth, x + width + stripeWidth, x + width},
                        new int[]{height, height, 0, 0},
                        4
                );
            }
        } finally {
            graphics.dispose();
        }
        writeJpeg(image, target, JPEG_PREVIEW_QUALITY_PERCENT / 100f);
    }

    private void writeGeneratedSeedVideo(Path target, long sizeBytes) throws IOException {
        byte[] header = "YINGSHI-SEED-MP4-PLACEHOLDER\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        try (OutputStream outputStream = Files.newOutputStream(target)) {
            outputStream.write(header);
            long remaining = sizeBytes - header.length;
            byte[] buffer = new byte[8192];
            int value = 0;
            while (remaining > 0) {
                int length = (int) Math.min(buffer.length, remaining);
                for (int index = 0; index < length; index++) {
                    buffer[index] = (byte) (value++ & 0xFF);
                }
                outputStream.write(buffer, 0, length);
                remaining -= length;
            }
        }
    }

    private boolean extractVideoFrame(Path sourcePath, Path targetPath, String seekSeconds) {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    "ffmpeg",
                    "-y",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-ss",
                    seekSeconds,
                    "-i",
                    sourcePath.toString(),
                    "-frames:v",
                    "1",
                    targetPath.toString()
            ).redirectErrorStream(true).start();
            boolean completed = process.waitFor(VIDEO_COVER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0 && Files.exists(targetPath) && Files.size(targetPath) > 0L;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private List<Path> findSeedSourceImages() throws IOException {
        Path seedRoot = rootPath.resolve("seed").normalize();
        List<Path> preferredImages = collectImages(rootPath.resolve("test").normalize(), seedRoot);
        if (!preferredImages.isEmpty()) {
            return preferJpegImages(preferredImages);
        }

        return preferJpegImages(collectImages(rootPath, seedRoot));
    }

    private List<Path> collectImages(Path searchRoot, Path excludedRoot) throws IOException {
        if (!Files.exists(searchRoot)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(searchRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> excludedRoot == null || !path.normalize().startsWith(excludedRoot))
                    .filter(path -> !hasUploadDirectorySegment(path))
                    .filter(this::isSupportedImageFile)
                    .filter(this::isReadableImageFile)
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    private boolean isSupportedImageFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg")
                || fileName.endsWith(".png")
                || fileName.endsWith(".webp")
                || fileName.endsWith(".bmp")
                || fileName.endsWith(".gif")
                || fileName.endsWith(".heic")
                || fileName.endsWith(".heif")
                || fileName.endsWith(".avif");
    }

    private List<Path> preferJpegImages(List<Path> images) {
        List<Path> jpegImages = images.stream()
                .filter(path -> {
                    String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg");
                })
                .toList();
        return jpegImages.isEmpty() ? images : jpegImages;
    }

    private boolean hasUploadDirectorySegment(Path path) {
        for (Path segment : path.normalize()) {
            if (segment.toString().toLowerCase(Locale.ROOT).startsWith("upload_")) {
                return true;
            }
        }
        return false;
    }

    private boolean isReadableImageFile(Path path) {
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            return image != null && image.getWidth() > 0 && image.getHeight() > 0;
        } catch (IOException exception) {
            return false;
        }
    }

    public record StoredFile(
            String storagePath,
            String storedFileName,
            String storageProvider,
            String bucket,
            String objectKey,
            String checksum,
            Long sizeBytes
    ) {
    }
}
