package com.yingshi.server.service.storage;

import com.yingshi.server.config.StorageProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalObjectStorageServiceTests {

    @Test
    void rangeReadReturnsOnlyRequestedBytes() throws Exception {
        var root = Files.createTempDirectory("yingshi-local-storage-test-");
        var service = new LocalObjectStorageService(new StorageProperties(
                "local",
                "yingshi-media",
                root.toString(),
                null,
                null,
                null,
                null
        ));
        byte[] body = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        service.put("originals/2026/05/media_range.bin", "application/octet-stream", (long) body.length, new ByteArrayInputStream(body));

        StoredObject range = service.getRange("originals/2026/05/media_range.bin", 4, 9);

        assertEquals(6L, range.metadata().sizeBytes());
        assertArrayEquals("456789".getBytes(StandardCharsets.UTF_8), range.resource().getInputStream().readAllBytes());
    }
}
