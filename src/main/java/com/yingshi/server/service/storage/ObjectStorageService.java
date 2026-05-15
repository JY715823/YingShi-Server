package com.yingshi.server.service.storage;

import java.io.InputStream;
import java.util.Optional;

public interface ObjectStorageService {

    String provider();

    String bucket();

    ObjectMetadata put(String objectKey, String contentType, Long sizeBytes, InputStream inputStream);

    StoredObject get(String objectKey);

    boolean exists(String objectKey);

    boolean delete(String objectKey);

    Optional<ObjectMetadata> getMetadata(String objectKey);
}
