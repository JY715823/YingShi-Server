package com.yingshi.server.service.storage;

import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.config.StorageProperties;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.FilterInputStream;
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
            writeChecksumSidecar(targetPath, checksum);
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
    public StoredObject getRange(String objectKey, long start, long endInclusive) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        Path path = resolveObjectPath(normalizedObjectKey);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Stored media file was not found.");
        }
        try {
            long totalSize = Files.size(path);
            if (start < 0 || endInclusive < start || start >= totalSize) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_STORAGE_ERROR, "Invalid storage object byte range.");
            }
            long boundedEnd = Math.min(endInclusive, totalSize - 1);
            long rangeLength = boundedEnd - start + 1;
            ObjectMetadata metadata = new ObjectMetadata(
                    normalizedObjectKey,
                    normalizeContentType(Files.probeContentType(path)),
                    rangeLength,
                    null,
                    Files.getLastModifiedTime(path).toMillis()
            );
            return new StoredObject(
                    normalizedObjectKey,
                    new LocalRangeResource(path, normalizedObjectKey, metadata, start, rangeLength),
                    metadata
            );
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to read storage object byte range.");
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.isRegularFile(resolveObjectPath(normalizeObjectKey(objectKey)));
    }

    @Override
    public boolean delete(String objectKey) {
        Path path = resolveObjectPath(normalizeObjectKey(objectKey));
        try {
            boolean deleted = Files.deleteIfExists(path);
            if (deleted) {
                Files.deleteIfExists(checksumSidecarPath(path));
            }
            return deleted;
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
                    readChecksumSidecar(path),
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
        String normalized = ObjectKeyPolicy.normalizeRelativeObjectKey(objectKey);
        Path path = Paths.get(normalized.replace("/", FileSystems.getDefault().getSeparator()));
        return path.toString().replace(FileSystems.getDefault().getSeparator(), "/");
    }

    private Path checksumSidecarPath(Path dataPath) {
        return dataPath.resolveSibling(dataPath.getFileName().toString() + ".sha256");
    }

    private String readChecksumSidecar(Path dataPath) throws IOException {
        Path sidecar = checksumSidecarPath(dataPath);
        if (Files.isRegularFile(sidecar)) {
            String cached = Files.readString(sidecar).trim();
            if (isValidSha256(cached)) {
                return cached;
            }
            // Sidecar exists but contains invalid data — remove and recompute
            Files.deleteIfExists(sidecar);
        }
        // Sidecar missing or invalid — compute and cache
        String computed = computeChecksum(dataPath);
        writeChecksumSidecar(dataPath, computed);
        return computed;
    }

    private static boolean isValidSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    private void writeChecksumSidecar(Path dataPath, String checksum) throws IOException {
        Path sidecar = checksumSidecarPath(dataPath);
        Files.writeString(sidecar, checksum);
    }

    private String computeChecksum(Path path) throws IOException {
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

    private static final class LocalRangeResource extends AbstractResource {
        private final Path path;
        private final String objectKey;
        private final ObjectMetadata metadata;
        private final long start;
        private final long length;

        private LocalRangeResource(Path path, String objectKey, ObjectMetadata metadata, long start, long length) {
            this.path = path;
            this.objectKey = objectKey;
            this.metadata = metadata;
            this.start = start;
            this.length = length;
        }

        @Override
        public String getDescription() {
            return "Local storage object byte range " + objectKey;
        }

        @Override
        public String getFilename() {
            return path.getFileName().toString();
        }

        @Override
        public long contentLength() {
            return metadata.sizeBytes() == null ? -1L : metadata.sizeBytes();
        }

        @Override
        public long lastModified() {
            return metadata.lastModifiedMillis() == null ? 0L : metadata.lastModifiedMillis();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new BoundedRangeInputStream(Files.newInputStream(path), start, length);
        }
    }

    private static final class BoundedRangeInputStream extends FilterInputStream {
        private long remaining;

        private BoundedRangeInputStream(InputStream source, long offset, long length) throws IOException {
            super(source);
            skipFully(source, offset);
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = super.read();
            if (value != -1) {
                remaining -= 1;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int maxLength = (int) Math.min(length, remaining);
            int read = super.read(buffer, offset, maxLength);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        private static void skipFully(InputStream source, long offset) throws IOException {
            long remainingToSkip = offset;
            while (remainingToSkip > 0) {
                long skipped = source.skip(remainingToSkip);
                if (skipped <= 0) {
                    if (source.read() == -1) {
                        throw new IOException("Could not skip to requested storage object byte range.");
                    }
                    skipped = 1;
                }
                remainingToSkip -= skipped;
            }
        }
    }
}
