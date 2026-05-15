package com.yingshi.server.service.storage;

import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.config.StorageProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorageService implements ObjectStorageService {

    private static final String LOCAL_PROVIDER = "local";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final StorageProperties storageProperties;
    private final Path rootPath;

    public LocalObjectStorageService(StorageProperties storageProperties) {
        if (!LOCAL_PROVIDER.equalsIgnoreCase(storageProperties.provider())) {
            throw new IllegalStateException("Only the local storage provider is implemented in Stage 16 step 2.");
        }
        this.storageProperties = storageProperties;
        this.rootPath = Paths.get(storageProperties.localRoot()).toAbsolutePath().normalize();
    }

    @Override
    public String provider() {
        return storageProperties.provider();
    }

    @Override
    public String bucket() {
        return storageProperties.bucket();
    }

    @Override
    public ObjectMetadata put(String objectKey, String contentType, Long sizeBytes, InputStream inputStream) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        Path targetPath = resolveObjectPath(normalizedObjectKey);
        try {
            Files.createDirectories(targetPath.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                Files.copy(digestInputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            String checksum = HexFormat.of().formatHex(digest.digest());
            long actualSizeBytes = Files.size(targetPath);
            return new ObjectMetadata(
                    normalizedObjectKey,
                    normalizeContentType(contentType),
                    actualSizeBytes,
                    checksum,
                    Files.getLastModifiedTime(targetPath).toMillis()
            );
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to write storage object.");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available.", exception);
        }
    }

    @Override
    public StoredObject get(String objectKey) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        Path path = resolveObjectPath(normalizedObjectKey);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Stored media file was not found.");
        }
        return new StoredObject(normalizedObjectKey, new FileSystemResource(path), fastMetadata(normalizedObjectKey, path));
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.isRegularFile(resolveObjectPath(normalizeObjectKey(objectKey)));
    }

    @Override
    public boolean delete(String objectKey) {
        Path path = resolveObjectPath(normalizeObjectKey(objectKey));
        try {
            return Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to delete storage object.");
        }
    }

    @Override
    public Optional<ObjectMetadata> getMetadata(String objectKey) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        Path path = resolveObjectPath(normalizedObjectKey);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ObjectMetadata(
                    normalizedObjectKey,
                    normalizeContentType(Files.probeContentType(path)),
                    Files.size(path),
                    checksum(path),
                    Files.getLastModifiedTime(path).toMillis()
            ));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to read storage object metadata.");
        }
    }

    private Path resolveObjectPath(String objectKey) {
        Path relativePath = Paths.get(objectKey.replace("/", FileSystems.getDefault().getSeparator()));
        if (relativePath.isAbsolute()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_STORAGE_ERROR, "Object key must be relative.");
        }
        Path resolvedPath = rootPath.resolve(relativePath).toAbsolutePath().normalize();
        if (!resolvedPath.startsWith(rootPath)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_STORAGE_ERROR, "Object key is outside of the configured local storage root.");
        }
        return resolvedPath;
    }

    private String normalizeObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_STORAGE_ERROR, "Object key must not be blank.");
        }
        String normalized = objectKey.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        Path path = Paths.get(normalized.replace("/", FileSystems.getDefault().getSeparator()));
        if (path.isAbsolute()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_STORAGE_ERROR, "Object key must be relative.");
        }
        for (Path segment : path) {
            String value = segment.toString();
            if (value.isBlank() || ".".equals(value) || "..".equals(value)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_STORAGE_ERROR, "Object key contains an unsafe path segment.");
            }
        }
        return path.toString().replace(FileSystems.getDefault().getSeparator(), "/");
    }

    private String checksum(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(path);
                 DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                digestInputStream.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available.", exception);
        }
    }

    private ObjectMetadata fastMetadata(String objectKey, Path path) {
        try {
            return new ObjectMetadata(
                    objectKey,
                    normalizeContentType(Files.probeContentType(path)),
                    Files.size(path),
                    null,
                    Files.getLastModifiedTime(path).toMillis()
            );
        } catch (IOException exception) {
            return new ObjectMetadata(objectKey, DEFAULT_CONTENT_TYPE, null, null, null);
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_CONTENT_TYPE;
        }
        return contentType;
    }
}
