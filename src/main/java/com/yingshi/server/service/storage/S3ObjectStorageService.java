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
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Service
@ConditionalOnExpression("'${app.storage.provider:local}' == 's3' || '${app.storage.provider:local}' == 'minio' || '${app.storage.provider:local}' == 'cos'")
public class S3ObjectStorageService implements ObjectStorageService {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final DateTimeFormatter DATE_STAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter AMZ_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final StorageProperties storageProperties;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Presigner publicPresigner;
    private final URI publicPresignerEndpoint;

    public S3ObjectStorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
        this.s3Client = buildClient(storageProperties);
        this.s3Presigner = buildPresigner(storageProperties);
        this.publicPresigner = buildPublicPresigner(storageProperties);
        String pubEndpoint = storageProperties.directUploadPublicEndpoint();
        this.publicPresignerEndpoint = pubEndpoint != null ? URI.create(pubEndpoint) : null;
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
    public List<String> listByPrefix(String prefix, String contains) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        String normalizedPrefix = normalizeObjectKey(prefix);
        List<String> result = new ArrayList<>();
        String continuationToken = null;
        try {
            do {
                ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                        .bucket(bucket())
                        .prefix(normalizedPrefix);
                if (continuationToken != null) {
                    requestBuilder.continuationToken(continuationToken);
                }
                ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());
                for (S3Object s3Object : response.contents()) {
                    String key = s3Object.key();
                    if (contains == null || contains.isBlank() || key.contains(contains)) {
                        result.add(key);
                    }
                }
                continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;
            } while (continuationToken != null);
        } catch (S3Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to list S3 objects by prefix.");
        }
        return result;
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
        // Custom SigV4 presigned URL that only signs host + content-type, NOT content-length.
        // AWS SDK v2 S3Presigner always signs content-length, which causes SignatureDoesNotMatch
        // on COS when the client's actual Content-Length differs from the pre-signed value.
        URI endpoint = publicPresignerEndpoint != null ? publicPresignerEndpoint : URI.create(storageProperties.endpoint());
        String normalizedContentType = normalizeContentType(contentType);
        String normalizedKey = normalizeObjectKey(objectKey);
        long expiresSeconds = Math.max(1, ttl.getSeconds());

        try {
            // Virtual-hosted style (default for COS/S3): bucket name as subdomain
            String host = storageProperties.forcePathStyle()
                    ? endpoint.getHost()
                    : bucket() + "." + endpoint.getHost();
            String path = storageProperties.forcePathStyle()
                    ? "/" + bucket() + "/" + normalizedKey
                    : "/" + normalizedKey;
            String dateStamp = ZonedDateTime.now(ZoneOffset.UTC).format(DATE_STAMP_FMT);
            String amzDate = ZonedDateTime.now(ZoneOffset.UTC).format(AMZ_DATE_FMT);
            String credentialScope = dateStamp + "/" + storageProperties.region() + "/s3/aws4_request";
            String credential = storageProperties.accessKey() + "/" + credentialScope;

            // Canonical headers: only host + content-type (NO content-length)
            TreeMap<String, String> canonicalHeaders = new TreeMap<>();
            canonicalHeaders.put("content-type", normalizedContentType);
            canonicalHeaders.put("host", host);
            String signedHeadersStr = "content-type;host";

            StringBuilder canonicalHeadersBlock = new StringBuilder();
            for (Map.Entry<String, String> e : canonicalHeaders.entrySet()) {
                canonicalHeadersBlock.append(e.getKey()).append(':').append(e.getValue()).append('\n');
            }

            // Query parameters for presigned URL
            TreeMap<String, String> queryParams = new TreeMap<>();
            queryParams.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
            queryParams.put("X-Amz-Credential", credential);
            queryParams.put("X-Amz-Date", amzDate);
            queryParams.put("X-Amz-Expires", String.valueOf(expiresSeconds));
            queryParams.put("X-Amz-SignedHeaders", signedHeadersStr);

            StringBuilder canonicalQueryString = new StringBuilder();
            for (Map.Entry<String, String> e : queryParams.entrySet()) {
                if (canonicalQueryString.length() > 0) canonicalQueryString.append('&');
                canonicalQueryString.append(uriEncode(e.getKey())).append('=').append(uriEncode(e.getValue()));
            }

            // Canonical request
            String canonicalRequest = "PUT\n"
                    + path + "\n"
                    + canonicalQueryString + "\n"
                    + canonicalHeadersBlock + "\n"
                    + signedHeadersStr + "\n"
                    + "UNSIGNED-PAYLOAD";

            // String to sign
            String hashedCanonicalRequest = sha256Hex(canonicalRequest);
            String stringToSign = "AWS4-HMAC-SHA256\n"
                    + amzDate + "\n"
                    + credentialScope + "\n"
                    + hashedCanonicalRequest;

            // Signing key
            byte[] kDate = hmacSha256(("AWS4" + storageProperties.secretKey()).getBytes(StandardCharsets.UTF_8), dateStamp);
            byte[] kRegion = hmacSha256(kDate, storageProperties.region());
            byte[] kService = hmacSha256(kRegion, "s3");
            byte[] kSigning = hmacSha256(kService, "aws4_request");

            // Signature
            String signature = bytesToHex(hmacSha256(kSigning, stringToSign));

            // Build presigned URL
            String presignedUrl = endpoint.getScheme() + "://" + host + path
                    + "?" + canonicalQueryString
                    + "&X-Amz-Signature=" + signature;

            // Headers for client (only content-type, no content-length)
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", normalizedContentType);

            long expirationMillis = System.currentTimeMillis() + (expiresSeconds * 1000);
            return Optional.of(new PresignedObjectUrl(presignedUrl, expirationMillis, headers));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR,
                    "Failed to generate presigned upload URL: " + e.getMessage());
        }
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
        S3Presigner presigner = publicPresigner != null ? publicPresigner : s3Presigner;
        PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
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

    private S3Presigner buildPublicPresigner(StorageProperties properties) {
        String publicEndpoint = properties.directUploadPublicEndpoint();
        if (publicEndpoint == null) {
            return null;
        }
        if (properties.accessKey() == null || properties.secretKey() == null) {
            return null;
        }
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .endpointOverride(URI.create(publicEndpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.forcePathStyle())
                        .build())
                .build();
    }

    public boolean isDirectUploadAvailable() {
        return publicPresigner != null;
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

    // ---- Manual SigV4 presigned URL helpers (bypasses SDK's mandatory content-length signing) ----

    private static String sha256Hex(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return bytesToHex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String uriEncode(String input) {
        return URLEncoder.encode(input, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
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
