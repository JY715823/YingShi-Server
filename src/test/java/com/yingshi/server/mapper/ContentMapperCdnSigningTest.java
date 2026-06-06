package com.yingshi.server.mapper;

import com.yingshi.server.config.StorageProperties;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.dto.content.MediaAccessDto;
import com.yingshi.server.dto.content.MediaDto;
import com.yingshi.server.service.storage.ObjectMetadata;
import com.yingshi.server.service.storage.ObjectStorageService;
import com.yingshi.server.service.storage.StoredObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ContentMapperCdnSigningTest {

    @Test
    void mediaAccessUsesSignedCdnUrlAndStableCacheKey() throws Exception {
        StorageProperties storageProperties = new StorageProperties(
                "cos",
                "yingshi-media",
                "local-storage",
                "https://yingshi-media.cos.ap-guangzhou.myqcloud.com",
                "ap-guangzhou",
                "cos-ak",
                "cos-sk",
                "https://cdn.example.com",
                "cdn-auth-key",
                "sign",
                "t",
                Duration.ofMinutes(15),
                true,
                false
        );
        ContentMapper mapper = new ContentMapper(storageProperties, new EmptyObjectStorageService());
        MediaEntity media = imageMedia();

        MediaDto dto = mapper.toMediaDto(media, List.of("album_001"));
        MediaAccessDto originalAccess = dto.access().stream()
                .filter(access -> "original".equals(access.variant()))
                .findFirst()
                .orElseThrow();

        URI signedUrl = URI.create(originalAccess.signedUrl());
        String timestamp = queryParameter(signedUrl.getRawQuery(), "t");
        String expectedSignature = md5Hex("cdn-auth-key" + "/originals/2026/06/media_001.jpg" + timestamp);

        assertThat(signedUrl.getHost()).isEqualTo("cdn.example.com");
        assertThat(signedUrl.getRawPath()).isEqualTo("/originals/2026/06/media_001.jpg");
        assertThat(queryParameter(signedUrl.getRawQuery(), "sign")).isEqualTo(expectedSignature);
        assertThat(originalAccess.cacheKey()).isEqualTo("media:media_001:original:etag_001");
        assertThat(originalAccess.expiresAtMillis()).isNotNull();
    }

    private static MediaEntity imageMedia() {
        MediaEntity media = new MediaEntity();
        media.setId("media_001");
        media.setLibraryId("library_001");
        media.setMediaType(MediaType.IMAGE);
        media.setUrl("/api/media/files/media_001");
        media.setPreviewUrl("/api/media/files/media_001?variant=preview");
        media.setOriginalUrl("/api/media/files/media_001");
        media.setMimeType("image/jpeg");
        media.setSizeBytes(1024L);
        media.setWidth(100);
        media.setHeight(100);
        media.setAspectRatio(1.0);
        media.setDisplayTimeMillis(1_780_000_000_000L);
        media.setImportedAtMillis(1_780_000_000_000L);
        media.setDisplayTimeSource("IMPORTED");
        media.setStoragePath("originals/2026/06/media_001.jpg");
        media.setOriginalObjectKey("originals/2026/06/media_001.jpg");
        media.setPreviewObjectKey("previews/2026/06/media_001_1280.jpg");
        media.setChecksum("etag_001");
        return media;
    }

    private static String queryParameter(String rawQuery, String name) {
        assertThat(rawQuery).isNotBlank();
        for (String part : rawQuery.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(name)) {
                return pair[1];
            }
        }
        return null;
    }

    private static String md5Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static final class EmptyObjectStorageService implements ObjectStorageService {

        @Override
        public String provider() {
            return "cos";
        }

        @Override
        public String bucket() {
            return "yingshi-media";
        }

        @Override
        public ObjectMetadata put(String objectKey, String contentType, Long sizeBytes, InputStream inputStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredObject get(String objectKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredObject getRange(String objectKey, long start, long endInclusive) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean exists(String objectKey) {
            return false;
        }

        @Override
        public boolean delete(String objectKey) {
            return false;
        }

        @Override
        public Optional<ObjectMetadata> getMetadata(String objectKey) {
            return Optional.empty();
        }
    }
}
