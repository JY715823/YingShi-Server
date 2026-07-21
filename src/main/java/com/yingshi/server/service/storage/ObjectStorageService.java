package com.yingshi.server.service.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface ObjectStorageService {

    String provider();

    String bucket();

    ObjectMetadata put(String objectKey, String contentType, Long sizeBytes, InputStream inputStream);

    StoredObject get(String objectKey);

    StoredObject getRange(String objectKey, long start, long endInclusive);

    boolean exists(String objectKey);

    boolean delete(String objectKey);

    Optional<ObjectMetadata> getMetadata(String objectKey);

    /**
     * 列出指定前缀下所有对象 key，并按包含字符串过滤。
     * 用于批量删除派生对象（如 MinIO 中某 media 的所有尺寸 preview/cover）。
     *
     * @param prefix   对象 key 前缀（如 "previews/2026/07/"）
     * @param contains 仅保留 key 中包含此子串的对象（如 "media_abc123-"），传 null 或空串表示不过滤
     * @return 匹配的对象 key 列表
     */
    default List<String> listByPrefix(String prefix, String contains) {
        return List.of();
    }

    default boolean supportsPresignedPut() {
        return false;
    }

    default boolean isDirectUploadAvailable() {
        return false;
    }

    default Optional<PresignedObjectUrl> presignPut(
            String objectKey,
            String contentType,
            Long sizeBytes,
            Duration ttl
    ) {
        return Optional.empty();
    }

    default Optional<PresignedObjectUrl> presignGet(String objectKey, Duration ttl) {
        return Optional.empty();
    }
}
