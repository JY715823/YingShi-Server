package com.yingshi.server.service.upload;

import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.config.StorageProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class LocalMediaStorageService {

    private final Path rootPath;

    public LocalMediaStorageService(StorageProperties storageProperties) {
        this.rootPath = Paths.get(storageProperties.localRoot()).toAbsolutePath().normalize();
    }

    public StoredFile store(String spaceId, String uploadId, String originalFileName, MultipartFile file) {
        String sanitizedName = sanitizeFileName(originalFileName);
        Path directory = rootPath.resolve(spaceId).resolve(uploadId);
        Path target = directory.resolve(sanitizedName).normalize();
        try {
            Files.createDirectories(directory);
            file.transferTo(target);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to store uploaded file.");
        }
        return new StoredFile(target.toString(), sanitizedName);
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

        Path previewDirectory = rootPath.resolve("_derived").resolve("previews").normalize();
        Path previewPath = previewDirectory.resolve(cacheKey + "-" + maxDimension + ".jpg").normalize();
        try {
            if (shouldRegeneratePreview(sourcePath, previewPath)) {
                Files.createDirectories(previewDirectory);
                BufferedImage sourceImage = ImageIO.read(sourcePath.toFile());
                if (sourceImage == null || sourceImage.getWidth() <= 0 || sourceImage.getHeight() <= 0) {
                    throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Preview could not be generated for this media.");
                }
                BufferedImage previewImage = resizeImage(sourceImage, maxDimension);
                ImageIO.write(previewImage, "jpg", previewPath.toFile());
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to generate local preview file.");
        }
        return new FileSystemResource(previewPath);
    }

    public String ensureSeedImage(String spaceId, String seedName, int sourceOffset) {
        Path spacePath = rootPath.resolve(spaceId).normalize();
        Path seedDirectory = spacePath.resolve("seed").normalize();
        Path target = seedDirectory.resolve(seedName + ".jpg").normalize();
        try {
            Files.createDirectories(seedDirectory);
            List<Path> sourceImages = findSeedSourceImages(spacePath);
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

    public List<Path> listFilesRecursively(String spaceId, String relativeDirectory) {
        Path directory = rootPath.resolve(spaceId).resolve(relativeDirectory).normalize();
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

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
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
            BufferedImage copied = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = copied.createGraphics();
            graphics.drawImage(sourceImage, 0, 0, null);
            graphics.dispose();
            return copied;
        }

        double scale = maxDimension / (double) longestEdge;
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(sourceImage, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return resized;
    }

    private List<Path> findSeedSourceImages(Path spacePath) throws IOException {
        Path seedRoot = spacePath.resolve("seed").normalize();
        List<Path> preferredImages = collectImages(spacePath.resolve("test").normalize(), seedRoot);
        if (!preferredImages.isEmpty()) {
            return preferJpegImages(preferredImages);
        }

        List<Path> spaceImages = collectImages(spacePath, seedRoot);
        if (!spaceImages.isEmpty()) {
            return preferJpegImages(spaceImages);
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

    public record StoredFile(String absolutePath, String storedFileName) {
    }
}
