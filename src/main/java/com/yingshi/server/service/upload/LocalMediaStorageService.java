package com.yingshi.server.service.upload;

import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.config.StorageProperties;
import com.yingshi.server.domain.MediaType;
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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
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

    private final Path rootPath;

    public LocalMediaStorageService(StorageProperties storageProperties) {
        this.rootPath = Paths.get(storageProperties.localRoot()).toAbsolutePath().normalize();
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
        try {
            Files.createDirectories(directory);
            file.transferTo(target);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to store uploaded file.");
        }
        return new StoredFile(toRelativeStoragePath(target), target.getFileName().toString());
    }

    public Resource load(String storagePath) {
        Path path = resolveStoragePath(storagePath);
        if (!Files.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Stored media file was not found.");
        }
        return new FileSystemResource(path);
    }

    public Resource loadPreview(String storagePath, String cacheKey, int maxDimension) {
        Path sourcePath = resolveStoragePath(storagePath);
        if (!Files.exists(sourcePath)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Stored media file was not found.");
        }

        Path previewPath = imagePreviewPath(sourcePath, cacheKey, maxDimension);
        if (!ensureImagePreview(storagePath, cacheKey, maxDimension)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Preview could not be generated for this media.");
        }
        return new FileSystemResource(previewPath);
    }

    public boolean ensureImagePreview(String storagePath, String cacheKey, int maxDimension) {
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
        Path sourcePath = resolveStoragePath(storagePath);
        if (!Files.exists(sourcePath)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Stored media file was not found.");
        }
        Path coverPath = videoCoverPath(sourcePath, cacheKey, maxDimension);
        if (!ensureVideoCover(storagePath, cacheKey, maxDimension)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Video cover could not be generated for this media.");
        }
        return new FileSystemResource(coverPath);
    }

    public boolean ensureVideoCover(String storagePath, String cacheKey, int maxDimension) {
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
            if (sourceImages.isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "No local seed images were found.");
            }
            Path source = sourceImages.get(Math.floorMod(seedName.hashCode() + sourceOffset, sourceImages.size()));
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to prepare seeded media file.");
        }
        return rootPath.relativize(target).toString().replace(FileSystems.getDefault().getSeparator(), "/");
    }

    public List<Path> listFilesRecursively(String relativeDirectory) {
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

    private Path resolveStoragePath(String storagePath) {
        Path rawPath = Paths.get(storagePath);
        if (rawPath.isAbsolute()) {
            return rawPath.toAbsolutePath().normalize();
        }
        return rootPath.resolve(rawPath).toAbsolutePath().normalize();
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

    public record StoredFile(String storagePath, String storedFileName) {
    }
}
