package com.yingshi.server.service.content;

import com.yingshi.server.config.StorageProperties;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.service.storage.LocalObjectStorageService;
import com.yingshi.server.service.upload.LocalMediaStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaStorageFieldServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void fillMissingStorageFieldsInfersRelativeKeyAndNormalizesMinioProviderName() {
        StorageProperties properties = new StorageProperties(
                "local",
                "yingshi-media",
                tempDir.toString(),
                null,
                "us-east-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        LocalMediaStorageService localMediaStorageService = new LocalMediaStorageService(
                properties,
                new LocalObjectStorageService(properties)
        );
        MediaStorageFieldService service = new MediaStorageFieldService(localMediaStorageService);

        MediaEntity media = new MediaEntity();
        media.setId("media_001");
        media.setMediaType(MediaType.IMAGE);
        media.setStorageProvider("minio");
        media.setStoragePath("originals/2026/04/media_001.jpg");

        assertTrue(service.fillMissingStorageFields(media));
        assertEquals("s3", media.getStorageProvider());
        assertEquals("yingshi-media", media.getBucket());
        assertEquals("originals/2026/04/media_001.jpg", media.getOriginalObjectKey());
        assertFalse(media.getOriginalObjectKey().contains("://"));

        media.setOriginalObjectKey("http://127.0.0.1:9000/yingshi-media/originals/2026/04/media_001.jpg");
        assertEquals("originals/2026/04/media_001.jpg", service.originalObjectKeyForRead(media));
        assertFalse(service.diagnose(media).objectKeyMissing());
        assertTrue(service.diagnose(media).objectKeyLooksLikeUrl());
    }

    @Test
    void fullUrlOnlyLegacyFieldsDoNotBecomeObjectKeys() {
        StorageProperties properties = new StorageProperties(
                "local",
                "yingshi-media",
                tempDir.toString(),
                null,
                "us-east-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        LocalMediaStorageService localMediaStorageService = new LocalMediaStorageService(
                properties,
                new LocalObjectStorageService(properties)
        );
        MediaStorageFieldService service = new MediaStorageFieldService(localMediaStorageService);

        MediaEntity media = new MediaEntity();
        media.setId("media_url_only");
        media.setMediaType(MediaType.IMAGE);
        media.setStoragePath("https://example.trycloudflare.com/api/media/files/media_url_only");
        media.setOriginalUrl("https://oss-cn-hangzhou.aliyuncs.com/bucket/originals/media_url_only.jpg");

        assertTrue(service.fillMissingStorageFields(media));
        assertNull(media.getOriginalObjectKey());
        assertNull(service.storagePathForRead(media));
        assertTrue(service.diagnose(media).objectKeyMissing());
    }
}
