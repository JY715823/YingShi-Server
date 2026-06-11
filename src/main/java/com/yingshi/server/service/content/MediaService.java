package com.yingshi.server.service.content;

import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.AlbumEntity;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.dto.content.MediaDto;
import com.yingshi.server.dto.content.MediaFeedPage;
import com.yingshi.server.mapper.ContentMapper;
import com.yingshi.server.repository.AlbumRepository;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.service.storage.ObjectKeyPolicy;
import com.yingshi.server.service.storage.ObjectMetadata;
import com.yingshi.server.service.upload.LocalMediaStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Service
public class MediaService {

    private static final int DEFAULT_FEED_PAGE_SIZE = 60;
    private static final int MAX_FEED_PAGE_SIZE = 120;
    private final MediaRepository mediaRepository;
    private final PostMediaRepository postMediaRepository;
    private final PostRepository postRepository;
    private final AlbumRepository albumRepository;
    private final ContentMapper contentMapper;
    private final LocalMediaStorageService localMediaStorageService;
    private final MediaStorageFieldService mediaStorageFieldService;
    private static final int PREVIEW_MAX_DIMENSION = 1280;
    private static final int VIDEO_COVER_MAX_DIMENSION = 1280;

    public MediaService(
            MediaRepository mediaRepository,
            PostMediaRepository postMediaRepository,
            PostRepository postRepository,
            AlbumRepository albumRepository,
            ContentMapper contentMapper,
            LocalMediaStorageService localMediaStorageService,
            MediaStorageFieldService mediaStorageFieldService
    ) {
        this.mediaRepository = mediaRepository;
        this.postMediaRepository = postMediaRepository;
        this.postRepository = postRepository;
        this.albumRepository = albumRepository;
        this.contentMapper = contentMapper;
        this.localMediaStorageService = localMediaStorageService;
        this.mediaStorageFieldService = mediaStorageFieldService;
    }

    public List<MediaDto> getMediaFeed(AuthenticatedUser currentUser) {
        String libraryId = currentUser.libraryId();
        List<MediaEntity> mediaItems = mediaRepository.findByLibraryIdAndDeletedAtIsNull(libraryId)
                .stream()
                .sorted(Comparator.comparing(MediaEntity::getDisplayTimeMillis).reversed().thenComparing(MediaEntity::getId))
                .toList();
        if (mediaItems.isEmpty()) {
            return List.of();
        }

        Map<String, PostEntity> activePostsById = postRepository
                .findByLibraryIdAndDeletedAtIsNullOrderByDisplayTimeMillisDescUpdatedAtDesc(libraryId)
                .stream()
                .collect(Collectors.toMap(PostEntity::getId, post -> post));
        Set<String> albumIds = activePostsById.values().stream().map(PostEntity::getAlbumId).collect(Collectors.toSet());
        Map<String, AlbumEntity> albumsById = albumIds.isEmpty()
                ? Map.of()
                : albumRepository.findByLibraryIdAndIdIn(libraryId, albumIds)
                .stream()
                .collect(Collectors.toMap(AlbumEntity::getId, album -> album));

        Set<String> activeRelatedMediaIds = new HashSet<>();
        Map<String, List<String>> postIdsByMediaId = new LinkedHashMap<>();
        for (PostMediaEntity relation : postMediaRepository.findByLibraryIdAndMediaIdIn(
                libraryId,
                mediaItems.stream().map(MediaEntity::getId).toList()
        )) {
            PostEntity post = activePostsById.get(relation.getPostId());
            if (post == null) {
                continue;
            }
            activeRelatedMediaIds.add(relation.getMediaId());
            AlbumEntity album = albumsById.get(post.getAlbumId());
            if (album != null && !Boolean.TRUE.equals(album.getIncludeInPhotoFeed())) {
                continue;
            }
            postIdsByMediaId.computeIfAbsent(relation.getMediaId(), key -> new ArrayList<>());
            List<String> postIds = postIdsByMediaId.get(relation.getMediaId());
            if (!postIds.contains(relation.getPostId())) {
                postIds.add(relation.getPostId());
            }
        }

        Map<String, DeduplicatedFeedEntry> deduplicatedEntries = new LinkedHashMap<>();
        for (MediaEntity media : mediaItems) {
            List<String> postIds = postIdsByMediaId.getOrDefault(media.getId(), List.of());
            boolean hasAnyActivePostRelation = activeRelatedMediaIds.contains(media.getId());
            if (!isRenderableMedia(media, postIds, hasAnyActivePostRelation)) {
                continue;
            }
            String deduplicationKey = feedDeduplicationKey(media);
            deduplicatedEntries
                    .computeIfAbsent(deduplicationKey, ignored -> new DeduplicatedFeedEntry(media, postIds, hasAnyActivePostRelation))
                    .merge(media, postIds, hasAnyActivePostRelation);
        }
        List<DeduplicatedFeedEntry> orderedEntries = deduplicatedEntries.values().stream()
                .sorted(Comparator
                        .comparing((DeduplicatedFeedEntry entry) -> entry.representativeMedia().getDisplayTimeMillis()).reversed()
                        .thenComparing(entry -> entry.representativeMedia().getId()))
                .toList();
        List<MediaDto> results = new ArrayList<>();
        for (DeduplicatedFeedEntry entry : orderedEntries) {
            results.add(contentMapper.toMediaDto(entry.representativeMedia(), entry.visiblePostIds()));
        }
        return results;
    }

    public MediaFeedPage getMediaFeedPage(AuthenticatedUser currentUser, String cursor, Integer pageSize) {
        int normalizedPageSize = normalizePageSize(pageSize);
        List<MediaDto> allItems = getMediaFeed(currentUser);
        if (allItems.isEmpty()) {
            return new MediaFeedPage(List.of(), null, false, normalizedPageSize);
        }

        Cursor decodedCursor = decodeCursor(cursor);
        int startIndex = decodedCursor == null ? 0 : indexAfterCursor(allItems, decodedCursor);
        if (startIndex >= allItems.size()) {
            return new MediaFeedPage(List.of(), null, false, normalizedPageSize);
        }

        int endExclusive = Math.min(startIndex + normalizedPageSize, allItems.size());
        List<MediaDto> pageItems = allItems.subList(startIndex, endExclusive);
        boolean hasMore = endExclusive < allItems.size();
        String nextCursor = hasMore && !pageItems.isEmpty()
                ? encodeCursor(pageItems.get(pageItems.size() - 1))
                : null;

        return new MediaFeedPage(pageItems, nextCursor, hasMore, normalizedPageSize);
    }

    @Transactional
    public MediaFilePayload loadMediaFile(String mediaId, String variant, AuthenticatedUser currentUser) {
        MediaEntity media = mediaRepository.findByIdAndLibraryId(mediaId, currentUser.libraryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Media was not found."));
        boolean changed = mediaStorageFieldService.fillMissingStorageFields(media);
        String storagePath = mediaStorageFieldService.storagePathForRead(media);
        if (storagePath == null || storagePath.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Local file is not available for this media.");
        }
        MediaResourceResolution resolution = resolveMediaResource(media, variant, storagePath);
        if (resolution.previewGenerated()) {
            changed = mediaStorageFieldService.markPreviewGenerated(media) || changed;
        }
        if (resolution.coverGenerated()) {
            changed = mediaStorageFieldService.markCoverGenerated(media) || changed;
        }
        if (changed) {
            mediaRepository.save(media);
        }
        String mimeType = isJpegVariant(media, variant)
                ? "image/jpeg"
                : media.getMimeType();
        ObjectMetadata metadata = localMediaStorageService.metadataForStoragePath(resolution.storagePath());
        Long contentLength = metadata == null ? null : metadata.sizeBytes();
        Long lastModifiedMillis = metadata == null ? null : metadata.lastModifiedMillis();
        MediaFilePayload.ResourceLoader resourceLoader = () -> localMediaStorageService.load(resolution.storagePath());
        boolean providerRange = ObjectKeyPolicy.isRelativeObjectKey(resolution.storagePath());
        MediaFilePayload.RangeResourceLoader rangeResourceLoader = (start, endInclusive) ->
                localMediaStorageService.loadRange(resolution.storagePath(), start, endInclusive);
        return new MediaFilePayload(resourceLoader, rangeResourceLoader, !providerRange, mimeType, contentLength, lastModifiedMillis);
    }

    private MediaResourceResolution resolveMediaResource(MediaEntity media, String variant, String storagePath) {
        if ("preview".equalsIgnoreCase(variant) && media.getMediaType() == com.yingshi.server.domain.MediaType.IMAGE) {
            try {
                String previewObjectKey = localMediaStorageService.imagePreviewObjectKey(storagePath, media.getId(), PREVIEW_MAX_DIMENSION);
                if (!localMediaStorageService.ensureImagePreview(storagePath, media.getId(), PREVIEW_MAX_DIMENSION)) {
                    return new MediaResourceResolution(storagePath, false, false);
                }
                return new MediaResourceResolution(previewObjectKey, true, false);
            } catch (ApiException exception) {
                return new MediaResourceResolution(storagePath, false, false);
            }
        }
        if (("cover".equalsIgnoreCase(variant) || "preview".equalsIgnoreCase(variant)) &&
                media.getMediaType() == com.yingshi.server.domain.MediaType.VIDEO) {
            String coverObjectKey = localMediaStorageService.videoCoverObjectKey(storagePath, media.getId(), VIDEO_COVER_MAX_DIMENSION);
            if (!localMediaStorageService.ensureVideoCover(storagePath, media.getId(), VIDEO_COVER_MAX_DIMENSION)) {
                throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Video cover could not be generated for this media.");
            }
            return new MediaResourceResolution(coverObjectKey, false, true);
        }
        return new MediaResourceResolution(storagePath, false, false);
    }

    private boolean isJpegVariant(MediaEntity media, String variant) {
        if (media.getMediaType() == com.yingshi.server.domain.MediaType.IMAGE) {
            return "preview".equalsIgnoreCase(variant);
        }
        return media.getMediaType() == com.yingshi.server.domain.MediaType.VIDEO &&
                ("preview".equalsIgnoreCase(variant) || "cover".equalsIgnoreCase(variant));
    }

    private boolean isRenderableMedia(MediaEntity media, List<String> visiblePostIds, boolean hasAnyActivePostRelation) {
        return !hasAnyActivePostRelation || !visiblePostIds.isEmpty();
    }

    private String feedDeduplicationKey(MediaEntity media) {
        String checksum = normalizeNullable(media.getChecksum());
        if (checksum != null) {
            return "checksum:" + media.getMediaType().name() + ":" + checksum;
        }
        String sourceFingerprint = normalizeNullable(media.getSourceFingerprint());
        if (sourceFingerprint != null) {
            return "fingerprint:" + sourceFingerprint;
        }
        return "shape:" + media.getMediaType().name()
                + "|" + normalizeNullable(media.getMimeType())
                + "|" + media.getSizeBytes()
                + "|" + media.getDisplayTimeMillis()
                + "|" + media.getWidth()
                + "|" + media.getHeight()
                + "|" + (media.getDurationMillis() == null ? "none" : media.getDurationMillis());
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int compareNullableLong(Long left, Long right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return Long.compare(left, right);
    }

    private boolean shouldPreferRepresentative(
            MediaEntity candidateMedia,
            List<String> candidatePostIds,
            boolean candidateHasActiveRelation,
            MediaEntity currentMedia,
            int currentVisiblePostCount,
            boolean currentHasActiveRelation
    ) {
        if (candidatePostIds.size() != currentVisiblePostCount) {
            return candidatePostIds.size() > currentVisiblePostCount;
        }
        if (candidateHasActiveRelation != currentHasActiveRelation) {
            return candidateHasActiveRelation;
        }
        int importedAtComparison = compareNullableLong(candidateMedia.getImportedAtMillis(), currentMedia.getImportedAtMillis());
        if (importedAtComparison != 0) {
            return importedAtComparison > 0;
        }
        int capturedAtComparison = compareNullableLong(candidateMedia.getCapturedAtMillis(), currentMedia.getCapturedAtMillis());
        if (capturedAtComparison != 0) {
            return capturedAtComparison > 0;
        }
        int displayTimeComparison = Long.compare(candidateMedia.getDisplayTimeMillis(), currentMedia.getDisplayTimeMillis());
        if (displayTimeComparison != 0) {
            return displayTimeComparison > 0;
        }
        return candidateMedia.getId().compareTo(currentMedia.getId()) > 0;
    }

    private final class DeduplicatedFeedEntry {
        private MediaEntity representativeMedia;
        private final LinkedHashSet<String> visiblePostIds = new LinkedHashSet<>();
        private boolean hasActiveRelation;

        private DeduplicatedFeedEntry(
                MediaEntity representativeMedia,
                List<String> initialPostIds,
                boolean hasActiveRelation
        ) {
            this.representativeMedia = representativeMedia;
            this.visiblePostIds.addAll(initialPostIds);
            this.hasActiveRelation = hasActiveRelation;
        }

        private void merge(
                MediaEntity candidateMedia,
                List<String> candidatePostIds,
                boolean candidateHasActiveRelation
        ) {
            int currentVisiblePostCount = visiblePostIds.size();
            if (shouldPreferRepresentative(
                    candidateMedia,
                    candidatePostIds,
                    candidateHasActiveRelation,
                    representativeMedia,
                    currentVisiblePostCount,
                    hasActiveRelation
            )) {
                representativeMedia = candidateMedia;
                hasActiveRelation = candidateHasActiveRelation;
            }
            visiblePostIds.addAll(candidatePostIds);
        }

        private MediaEntity representativeMedia() {
            return representativeMedia;
        }

        private List<String> visiblePostIds() {
            return List.copyOf(visiblePostIds);
        }
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_FEED_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_FEED_PAGE_SIZE);
    }

    private int indexAfterCursor(List<MediaDto> items, Cursor cursor) {
        for (int index = 0; index < items.size(); index++) {
            MediaDto item = items.get(index);
            if (item.displayTimeMillis().equals(cursor.displayTimeMillis()) && item.mediaId().equals(cursor.mediaId())) {
                return index + 1;
            }
        }
        for (int index = 0; index < items.size(); index++) {
            MediaDto item = items.get(index);
            if (item.displayTimeMillis() < cursor.displayTimeMillis()) {
                return index;
            }
            if (item.displayTimeMillis().equals(cursor.displayTimeMillis()) && item.mediaId().compareTo(cursor.mediaId()) > 0) {
                return index;
            }
        }
        return items.size();
    }

    private String encodeCursor(MediaDto item) {
        String rawCursor = item.displayTimeMillis() + "|" + item.mediaId();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String rawCursor = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = rawCursor.split("\\|", 2);
            if (parts.length != 2 || parts[1].isBlank()) {
                return null;
            }
            return new Cursor(Long.parseLong(parts[0]), parts[1]);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record Cursor(long displayTimeMillis, String mediaId) {
    }

    private record MediaResourceResolution(
            String storagePath,
            boolean previewGenerated,
            boolean coverGenerated
    ) {
    }
}
