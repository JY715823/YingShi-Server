package com.yingshi.server.service.content;

import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.service.storage.ObjectKeyPolicy;
import com.yingshi.server.service.upload.LocalMediaStorageService;
import org.springframework.stereotype.Service;

@Service
public class MediaStorageFieldService {

    private static final int PREVIEW_MAX_DIMENSION = 1280;
    private static final int VIDEO_COVER_MAX_DIMENSION = 1280;

    private final LocalMediaStorageService localMediaStorageService;

    public MediaStorageFieldService(LocalMediaStorageService localMediaStorageService) {
        this.localMediaStorageService = localMediaStorageService;
    }

    public boolean fillMissingStorageFields(MediaEntity media) {
        boolean changed = false;
        if (isBlank(media.getStorageProvider())) {
            media.setStorageProvider(localMediaStorageService.provider());
            changed = true;
        } else if ("minio".equalsIgnoreCase(media.getStorageProvider())) {
            media.setStorageProvider("s3");
            changed = true;
        }
        if (isBlank(media.getBucket())) {
            media.setBucket(localMediaStorageService.bucket());
            changed = true;
        }
        if (isBlank(media.getOriginalObjectKey())) {
            String inferredObjectKey = inferOriginalObjectKey(media);
            if (inferredObjectKey != null) {
                media.setOriginalObjectKey(inferredObjectKey);
                changed = true;
            }
        } else {
            String normalizedObjectKey = ObjectKeyPolicy.tryNormalizeRelativeObjectKey(media.getOriginalObjectKey());
            if (normalizedObjectKey != null && !normalizedObjectKey.equals(media.getOriginalObjectKey())) {
                media.setOriginalObjectKey(normalizedObjectKey);
                changed = true;
            }
        }
        if (isBlank(media.getChecksum())) {
            var metadata = localMediaStorageService.metadataForObjectKey(media.getOriginalObjectKey());
            if (metadata != null && !isBlank(metadata.checksum())) {
                media.setChecksum(metadata.checksum());
                changed = true;
            }
        }
        return changed;
    }

    public boolean markPreviewGenerated(MediaEntity media) {
        if (media.getMediaType() != MediaType.IMAGE) {
            return false;
        }
        String storagePath = storagePathForDerivedObject(media);
        if (storagePath == null) {
            return false;
        }
        String previewObjectKey = localMediaStorageService.imagePreviewObjectKey(
                storagePath,
                media.getId(),
                PREVIEW_MAX_DIMENSION
        );
        if (previewObjectKey.equals(media.getPreviewObjectKey())) {
            return false;
        }
        media.setPreviewObjectKey(previewObjectKey);
        return true;
    }

    public boolean markCoverGenerated(MediaEntity media) {
        if (media.getMediaType() != MediaType.VIDEO) {
            return false;
        }
        String storagePath = storagePathForDerivedObject(media);
        if (storagePath == null) {
            return false;
        }
        String coverObjectKey = localMediaStorageService.videoCoverObjectKey(
                storagePath,
                media.getId(),
                VIDEO_COVER_MAX_DIMENSION
        );
        if (coverObjectKey.equals(media.getCoverObjectKey())) {
            return false;
        }
        media.setCoverObjectKey(coverObjectKey);
        return true;
    }

    public String originalObjectKeyForRead(MediaEntity media) {
        String normalized = ObjectKeyPolicy.tryNormalizeRelativeObjectKey(media.getOriginalObjectKey());
        if (normalized != null) {
            return normalized;
        }
        return inferOriginalObjectKey(media);
    }

    public String storagePathForRead(MediaEntity media) {
        String objectKey = originalObjectKeyForRead(media);
        if (objectKey != null) {
            return objectKey;
        }
        if (isBlank(media.getStoragePath()) || ObjectKeyPolicy.looksLikeFullUrl(media.getStoragePath())) {
            return null;
        }
        return media.getStoragePath();
    }

    public StorageObjectDiagnostics diagnose(MediaEntity media) {
        String objectKey = originalObjectKeyForRead(media);
        boolean objectKeyMissing = objectKey == null;
        boolean originalObjectKeyLooksLikeUrl = ObjectKeyPolicy.looksLikeFullUrl(media.getOriginalObjectKey());
        boolean previewObjectKeyLooksLikeUrl = ObjectKeyPolicy.looksLikeFullUrl(media.getPreviewObjectKey());
        boolean coverObjectKeyLooksLikeUrl = ObjectKeyPolicy.looksLikeFullUrl(media.getCoverObjectKey());
        boolean objectExists = objectKey != null && localMediaStorageService.objectExists(objectKey);
        return new StorageObjectDiagnostics(
                media.getId(),
                normalizeProvider(media.getStorageProvider()),
                firstNonBlank(media.getBucket(), localMediaStorageService.bucket()),
                objectKey,
                objectKeyMissing,
                originalObjectKeyLooksLikeUrl || previewObjectKeyLooksLikeUrl || coverObjectKeyLooksLikeUrl,
                objectExists
        );
    }

    private String inferOriginalObjectKey(MediaEntity media) {
        String storagePathKey = localMediaStorageService.originalObjectKey(media.getStoragePath());
        if (storagePathKey != null) {
            return storagePathKey;
        }
        String urlKey = localMediaStorageService.originalObjectKey(media.getOriginalUrl());
        if (urlKey != null) {
            return urlKey;
        }
        return localMediaStorageService.originalObjectKey(media.getUrl());
    }

    private String storagePathForDerivedObject(MediaEntity media) {
        String storagePath = storagePathForRead(media);
        if (storagePath == null) {
            return null;
        }
        return ObjectKeyPolicy.tryNormalizeRelativeObjectKey(storagePath);
    }

    private String normalizeProvider(String provider) {
        if (isBlank(provider)) {
            return localMediaStorageService.provider();
        }
        if ("minio".equalsIgnoreCase(provider)) {
            return "s3";
        }
        return provider.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String firstNonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record StorageObjectDiagnostics(
            String mediaId,
            String storageProvider,
            String bucket,
            String objectKey,
            boolean objectKeyMissing,
            boolean objectKeyLooksLikeUrl,
            boolean objectExists
    ) {
    }
}
