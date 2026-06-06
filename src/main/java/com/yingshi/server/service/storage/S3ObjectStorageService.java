package com.yingshi.server.service.storage;

import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.config.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.AbstractResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnExpression("'${app.storage.provider:local}' == 's3' || '${app.storage.provider:local}' == 'minio' || '${app.storage.provider:local}' == 'cos'")
public class S3ObjectStorageService implements ObjectStorageService {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final StorageProperties storageProperties;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public S3ObjectStorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
        this.s3Client = buildClient(storageProperties);
        this.s3Presigner = buildPresigner(storageProperties);
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
        long contentLength = requireSize(sizeBytes);
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket())
                    .key(normalizedObjectKey)
                    .contentType(normalizeContentType(contentType))
                    .contentLength(contentLength)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
            return getMetadata(normalizedObjectKey)
                    .orElseGet(() -> new ObjectMetadata(
                            normalizedObjectKey,
                            normalizeContentType(contentType),
                            contentLength,
                            null,
                            null
                    ));
        } catch (S3Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to write S3 storage object.");
        }
    }

    @Override
    public StoredObject get(String objectKey) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket())
                    .key(normalizedObjectKey)
                    .build();
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
            ObjectMetadata metadata = new ObjectMetadata(
                    normalizedObjectKey,
                    normalizeContentType(response.response().contentType()),
                    response.response().contentLength(),
                    null,
                    response.response().lastModified() == null ? null : response.response().lastModified().toEpochMilli()
            );
            return new StoredObject(normalizedObjectKey, new S3ObjectResource(response, normalizedObjectKey, metadata), metadata);
        } catch (NoSuchKeyException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Stored media file was not found.");
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Stored media file was not found.");
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to read S3 storage object.");
        }
    }

    @Override
    public StoredObject getRange(String objectKey, long start, long endInclusive) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        if (start < 0 || endInclusive < start) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_STORAGE_ERROR, "Invalid S3 object byte range.");
        }
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket())
                    .key(normalizedObjectKey)
                    .range("bytes=" + start + "-" + endInclusive)
                    .build();
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
            long rangeLength = endInclusive - start + 1;
            ObjectMetadata metadata = new ObjectMetadata(
                    normalizedObjectKey,
                    normalizeContentType(response.response().contentType()),
                    rangeLength,
                    null,
                    response.response().lastModified() == null ? null : response.response().lastModified().toEpochMilli()
            );
            return new StoredObject(normalizedObjectKey, new S3ObjectResource(response, normalizedObjectKey, metadata), metadata);
        } catch (NoSuchKeyException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Stored media file was not found.");
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Stored media file was not found.");
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to read S3 storage object byte range.");
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return getMetadata(objectKey).isPresent();
    }

    @Override
    public boolean delete(String objectKey) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket())
                    .key(normalizedObjectKey)
                    .build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to delete S3 storage object.");
        }
    }

    @Override
    public Optional<ObjectMetadata> getMetadata(String objectKey) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket())
                    .key(normalizedObjectKey)
                    .build());
            return Optional.of(new ObjectMetadata(
                    normalizedObjectKey,
                    normalizeContentType(response.contentType()),
                    response.contentLength(),
                    trimQuotes(response.eTag()),
                    response.lastModified() == null ? null : response.lastModified().toEpochMilli()
            ));
        } catch (NoSuchKeyException exception) {
            return Optional.empty();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to read S3 storage object metadata.");
        }
    }

    @Override
    public boolean supportsPresignedPut() {
        return true;
    }

    @Override
    public Optional<PresignedObjectUrl> presignPut(
            String objectKey,
            String contentType,
            Long sizeBytes,
            Duration ttl
    ) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket())
                .key(normalizedObjectKey)
                .contentType(normalizeContentType(contentType))
                .contentLength(requireSize(sizeBytes))
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(putObjectRequest)
                .build();
        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        Map<String, String> headers = flattenHeaders(presignedRequest.signedHeaders());
        headers.putIfAbsent("Content-Type", normalizeContentType(contentType));
        return Optional.of(new PresignedObjectUrl(
                presignedRequest.url().toString(),
                presignedRequest.expiration().toEpochMilli(),
                headers
        ));
    }

    @Override
    public Optional<PresignedObjectUrl> presignGet(String objectKey, Duration ttl) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket())
                .key(normalizedObjectKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getObjectRequest)
                .build();
        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return Optional.of(new PresignedObjectUrl(
                presignedRequest.url().toString(),
                presignedRequest.expiration().toEpochMilli(),
                flattenHeaders(presignedRequest.signedHeaders())
        ));
    }

    private S3Client buildClient(StorageProperties properties) {
        if (properties.endpoint() == null || properties.accessKey() == null || properties.secretKey() == null) {
            throw new IllegalStateException("S3 storage requires endpoint, access key, and secret key.");
        }
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .endpointOverride(URI.create(properties.endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.forcePathStyle())
                        .build())
                .build();
    }

    private S3Presigner buildPresigner(StorageProperties properties) {
        if (properties.endpoint() == null || properties.accessKey() == null || properties.secretKey() == null) {
            throw new IllegalStateException("S3 storage requires endpoint, access key, and secret key.");
        }
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .endpointOverride(URI.create(properties.endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.forcePathStyle())
                        .build())
                .build();
    }

    private long requireSize(Long sizeBytes) {
        if (sizeBytes == null || sizeBytes < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_STORAGE_ERROR, "S3 object writes require a known content length.");
        }
        return sizeBytes;
    }

    private String normalizeObjectKey(String objectKey) {
        return ObjectKeyPolicy.normalizeRelativeObjectKey(objectKey);
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_CONTENT_TYPE;
        }
        return contentType;
    }

    private String trimQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private Map<String, String> flattenHeaders(Map<String, List<String>> signedHeaders) {
        Map<String, String> headers = new LinkedHashMap<>();
        signedHeaders.forEach((name, values) -> {
            if (values != null && !values.isEmpty()) {
                headers.put(name, String.join(",", values));
            }
        });
        return headers;
    }

    private static final class S3ObjectResource extends AbstractResource {
        private final ResponseInputStream<GetObjectResponse> inputStream;
        private final String objectKey;
        private final ObjectMetadata metadata;
        private boolean opened;

        private S3ObjectResource(
                ResponseInputStream<GetObjectResponse> inputStream,
                String objectKey,
                ObjectMetadata metadata
        ) {
            this.inputStream = inputStream;
            this.objectKey = objectKey;
            this.metadata = metadata;
        }

        @Override
        public String getDescription() {
            return "S3 object " + objectKey;
        }

        @Override
        public String getFilename() {
            int slashIndex = objectKey.lastIndexOf('/');
            return slashIndex >= 0 ? objectKey.substring(slashIndex + 1) : objectKey;
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
        public boolean isOpen() {
            return true;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (opened) {
                throw new IOException("S3 object stream has already been opened.");
            }
            opened = true;
            return inputStream;
        }
    }
}
