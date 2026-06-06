package com.yingshi.server.mapper;

import com.yingshi.server.domain.AlbumEntity;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.config.StorageProperties;
import com.yingshi.server.dto.content.AlbumDto;
import com.yingshi.server.dto.content.MediaAccessDto;
import com.yingshi.server.dto.content.MediaDto;
import com.yingshi.server.dto.content.PostDetailDto;
import com.yingshi.server.dto.content.PostMediaDto;
import com.yingshi.server.dto.content.PostSummaryDto;
import com.yingshi.server.service.storage.ObjectKeyPolicy;
import com.yingshi.server.service.storage.ObjectStorageService;
import com.yingshi.server.service.storage.PresignedObjectUrl;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class ContentMapper {

    private static final int PREVIEW_MAX_DIMENSION = 1280;
    private static final int VIDEO_COVER_MAX_DIMENSION = 1280;
    private final StorageProperties storageProperties;
    private final ObjectStorageService objectStorageService;

    public ContentMapper(
            StorageProperties storageProperties,
            ObjectStorageService objectStorageService
    ) {
        this.storageProperties = storageProperties;
        this.objectStorageService = objectStorageService;
    }

    public AlbumDto toAlbumDto(AlbumEntity album, long smallAlbumCount) {
        return new AlbumDto(
                album.getId(),
                album.getTitle(),
                album.getSubtitle() == null ? "" : album.getSubtitle(),
                album.getCoverMediaId(),
                album.getSystemKey(),
                Boolean.TRUE.equals(album.getIncludeInPhotoFeed()),
                smallAlbumCount
        );
    }

    public PostSummaryDto toPostSummaryDto(PostEntity post, String albumId, String coverMediaId, long mediaCount) {
        return new PostSummaryDto(
                post.getId(),
                post.getTitle(),
                post.getSummary(),
                post.getContributorLabel(),
                post.getCreatorUserId(),
                splitUserIds(post.getParticipantUserIds()),
                post.getDisplayTimeMillis(),
                post.getEventStartedAtMillis(),
                post.getEventEndedAtMillis(),
                post.getDisplayTimeSource(),
                albumId,
                post.getSystemKey(),
                coverMediaId,
                mediaCount
        );
    }

    public PostDetailDto toPostDetailDto(
            PostEntity post,
            String albumId,
            String coverMediaId,
            long mediaCount,
            List<PostMediaDto> mediaItems
    ) {
        return new PostDetailDto(
                post.getId(),
                post.getTitle(),
                post.getSummary(),
                post.getContributorLabel(),
                post.getCreatorUserId(),
                splitUserIds(post.getParticipantUserIds()),
                post.getDisplayTimeMillis(),
                post.getEventStartedAtMillis(),
                post.getEventEndedAtMillis(),
                post.getDisplayTimeSource(),
                albumId,
                post.getSystemKey(),
                coverMediaId,
                mediaCount,
                mediaItems
            );
    }

    public PostMediaDto toPostMediaDto(PostMediaEntity relation, MediaDto mediaDto, boolean isCover) {
        return new PostMediaDto(
                relation.getSortOrder(),
                isCover,
                mediaDto
        );
    }

    public MediaDto toMediaDto(MediaEntity media, List<String> smallAlbumIds) {
        List<String> normalizedSmallAlbumIds = smallAlbumIds == null ? List.of() : smallAlbumIds;
        String localMediaUrl = localMediaUrl(media);
        String previewMediaUrl = previewMediaUrl(media);
        String originalUrl = media.getMediaType() == MediaType.IMAGE && localMediaUrl != null
                ? localMediaUrl
                : media.getOriginalUrl();
        String videoUrl = media.getMediaType() == MediaType.VIDEO && localMediaUrl != null
                ? localMediaUrl
                : media.getVideoUrl();
        String coverUrl = media.getMediaType() == MediaType.VIDEO && localMediaUrl != null
                ? localMediaUrl + "?variant=cover"
                : media.getCoverUrl();
        return new MediaDto(
                media.getId(),
                media.getMediaType().name().toLowerCase(Locale.ROOT),
                localMediaUrl != null ? localMediaUrl : media.getUrl(),
                previewMediaUrl != null ? previewMediaUrl : media.getPreviewUrl(),
                originalUrl,
                videoUrl,
                media.getMediaType() == MediaType.VIDEO ? coverUrl : null,
                media.getMimeType(),
                media.getSizeBytes(),
                media.getWidth(),
                media.getHeight(),
                media.getAspectRatio(),
                media.getDurationMillis(),
                media.getDisplayTimeMillis(),
                media.getCapturedAtMillis(),
                media.getImportedAtMillis(),
                media.getDisplayTimeSource(),
                media.getRecordOwnerUserId(),
                media.getUploadedByUserId(),
                normalizedSmallAlbumIds,
                access(media, localMediaUrl, previewMediaUrl, originalUrl, videoUrl, coverUrl)
        );
    }

    private List<MediaAccessDto> access(
            MediaEntity media,
            String localMediaUrl,
            String previewMediaUrl,
            String originalUrl,
            String videoUrl,
            String coverUrl
    ) {
        List<MediaAccessDto> access = new ArrayList<>();
        String revision = revision(media);
        if (media.getMediaType() == MediaType.IMAGE) {
            access.add(accessItem(media, "preview", previewMediaUrl, previewObjectKey(media), revision));
            access.add(accessItem(media, "original", originalUrl, originalObjectKey(media), revision));
        } else if (media.getMediaType() == MediaType.VIDEO) {
            access.add(accessItem(media, "cover", coverUrl, coverObjectKey(media), revision));
            access.add(accessItem(media, "video", videoUrl != null ? videoUrl : localMediaUrl, originalObjectKey(media), revision));
        } else if (localMediaUrl != null) {
            access.add(accessItem(media, "original", localMediaUrl, originalObjectKey(media), revision));
        }
        return access;
    }

    private MediaAccessDto accessItem(
            MediaEntity media,
            String variant,
            String fallbackUrl,
            String objectKey,
            String revision
    ) {
        SignedAccess signedAccess = signedAccess(objectKey);
        return new MediaAccessDto(
                variant,
                fallbackUrl,
                signedAccess.url(),
                signedAccess.expiresAtMillis(),
                "media:" + media.getId() + ":" + variant + ":" + revision,
                revision
        );
    }

    private SignedAccess signedAccess(String objectKey) {
        String normalizedObjectKey = ObjectKeyPolicy.tryNormalizeRelativeObjectKey(objectKey);
        if (normalizedObjectKey == null) {
            return new SignedAccess(null, null);
        }
        String cdnDomain = storageProperties.cdnDomain();
        if (cdnDomain != null) {
            Instant now = Instant.now();
            long expiresAtMillis = now.plus(storageProperties.signedUrlTtl()).toEpochMilli();
            return new SignedAccess(cdnUrl(cdnDomain, normalizedObjectKey, now.getEpochSecond()), expiresAtMillis);
        }
        Optional<PresignedObjectUrl> presignedUrl = objectStorageService.presignGet(
                normalizedObjectKey,
                storageProperties.signedUrlTtl()
        );
        return presignedUrl
                .map(value -> new SignedAccess(value.url(), value.expiresAtMillis()))
                .orElseGet(() -> new SignedAccess(null, null));
    }

    private String cdnUrl(String cdnDomain, String objectKey, long timestampSeconds) {
        String normalizedDomain = cdnDomain.trim();
        if (!normalizedDomain.startsWith("http://") && !normalizedDomain.startsWith("https://")) {
            normalizedDomain = "https://" + normalizedDomain;
        }
        String unsignedUrl = normalizedDomain.replaceAll("/+$", "") + "/" + objectKey;
        String authKey = storageProperties.cdnAuthKey();
        if (authKey == null) {
            return unsignedUrl;
        }
        String path = "/" + objectKey;
        String timestamp = Long.toString(timestampSeconds);
        String signature = md5Hex(authKey + path + timestamp);
        String separator = unsignedUrl.contains("?") ? "&" : "?";
        return unsignedUrl
                + separator
                + storageProperties.cdnSignParam()
                + "="
                + signature
                + "&"
                + storageProperties.cdnTimestampParam()
                + "="
                + timestamp;
    }

    private String md5Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 digest is required for CDN URL signing.", exception);
        }
    }

    private String originalObjectKey(MediaEntity media) {
        String objectKey = ObjectKeyPolicy.tryNormalizeRelativeObjectKey(media.getOriginalObjectKey());
        if (objectKey != null) {
            return objectKey;
        }
        return ObjectKeyPolicy.tryNormalizeRelativeObjectKey(media.getStoragePath());
    }

    private String previewObjectKey(MediaEntity media) {
        return ObjectKeyPolicy.tryNormalizeRelativeObjectKey(media.getPreviewObjectKey());
    }

    private String coverObjectKey(MediaEntity media) {
        return ObjectKeyPolicy.tryNormalizeRelativeObjectKey(media.getCoverObjectKey());
    }

    private String revision(MediaEntity media) {
        if (media.getChecksum() != null && !media.getChecksum().isBlank()) {
            return media.getChecksum().trim();
        }
        if (media.getUpdatedAt() != null) {
            return Long.toString(media.getUpdatedAt().toEpochMilli());
        }
        if (media.getImportedAtMillis() != null) {
            return Long.toString(media.getImportedAtMillis());
        }
        return Long.toString(media.getDisplayTimeMillis());
    }

    private String localMediaUrl(MediaEntity media) {
        String storagePath = media.getStoragePath();
        String originalObjectKey = media.getOriginalObjectKey();
        if ((storagePath == null || storagePath.isBlank())
                && !ObjectKeyPolicy.isRelativeObjectKey(originalObjectKey)) {
            return null;
        }
        return "/api/media/files/" + media.getId();
    }

    private String previewMediaUrl(MediaEntity media) {
        String localMediaUrl = localMediaUrl(media);
        if (localMediaUrl == null) {
            return null;
        }
        if (media.getMediaType() == MediaType.IMAGE) {
            return localMediaUrl + "?variant=preview";
        }
        return null;
    }

    private List<String> splitUserIds(String rawUserIds) {
        if (rawUserIds == null || rawUserIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawUserIds.split(","))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .distinct()
                .toList();
    }

    private record SignedAccess(String url, Long expiresAtMillis) {
    }
}
