package com.yingshi.server.service.content;

import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.cursor.CursorCodec;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.AlbumEntity;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.dto.content.MediaDto;
import com.yingshi.server.dto.content.MediaFeedPage;
import com.yingshi.server.dto.content.MediaImportStatusDto;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Service
public class MediaService {

    // R2-E-7: pageSize 规格对齐 - DEFAULT 30 / MAX 100 (与 MediaController defaultValue="30" 一致)
    private static final int DEFAULT_FEED_PAGE_SIZE = 30;
    private static final int MAX_FEED_PAGE_SIZE = 100;
    private static final int FEED_OVER_FETCH_MARGIN = 60;
    private final MediaRepository mediaRepository;
    private final PostMediaRepository postMediaRepository;
    private final PostRepository postRepository;
    private final AlbumRepository albumRepository;
    private final ContentMapper contentMapper;
    private final LocalMediaStorageService localMediaStorageService;
    private final MediaStorageFieldService mediaStorageFieldService;
    private final CursorCodec cursorCodec;
    private static final int PREVIEW_MAX_DIMENSION = 1280;
    private static final int VIDEO_COVER_MAX_DIMENSION = 1280;
    private static final int MAX_SEEN_DEDUP_KEYS = 300;

    public MediaService(
            MediaRepository mediaRepository,
            PostMediaRepository postMediaRepository,
            PostRepository postRepository,
            AlbumRepository albumRepository,
            ContentMapper contentMapper,
            LocalMediaStorageService localMediaStorageService,
            MediaStorageFieldService mediaStorageFieldService,
            CursorCodec cursorCodec
    ) {
        this.mediaRepository = mediaRepository;
        this.postMediaRepository = postMediaRepository;
        this.postRepository = postRepository;
        this.albumRepository = albumRepository;
        this.contentMapper = contentMapper;
        this.localMediaStorageService = localMediaStorageService;
        this.mediaStorageFieldService = mediaStorageFieldService;
        this.cursorCodec = cursorCodec;
    }

    public List<MediaDto> getMediaFeed(AuthenticatedUser currentUser) {
        String libraryId = currentUser.libraryId();
        // P1-3 隔离修复 S1: 排除 life domain 媒体, 防止照片流返回今日痕迹的媒体
        List<MediaEntity> mediaItems = mediaRepository.findByLibraryIdAndDeletedAtIsNullAndDomainNotLife(libraryId)
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

    @Transactional(readOnly = true)
    public MediaFeedPage getMediaFeedPage(AuthenticatedUser currentUser, String cursor, Integer pageSize) {
        int normalizedPageSize = normalizePageSize(pageSize);
        Cursor decodedCursor = decodeCursor(cursor);

        Long cursorDisplayTime = decodedCursor != null ? decodedCursor.displayTimeMillis() : null;
        String cursorMediaId = decodedCursor != null ? decodedCursor.mediaId() : null;
        Set<String> seenDedupKeys = decodedCursor != null ? decodedCursor.seenDedupKeys() : new LinkedHashSet<>();

        int fetchLimit = normalizedPageSize + FEED_OVER_FETCH_MARGIN;
        List<MediaEntity> mediaBatch = mediaRepository.findFeedPage(
                currentUser.libraryId(),
                cursorDisplayTime,
                cursorMediaId,
                org.springframework.data.domain.PageRequest.of(0, fetchLimit,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Order.desc("displayTimeMillis"),
                                org.springframework.data.domain.Sort.Order.asc("id")
                        ))
        );

        if (mediaBatch.isEmpty()) {
            return new MediaFeedPage(List.of(), null, false, normalizedPageSize);
        }

        String libraryId = currentUser.libraryId();
        List<String> batchMediaIds = mediaBatch.stream().map(MediaEntity::getId).toList();

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
        for (PostMediaEntity relation : postMediaRepository.findByLibraryIdAndMediaIdIn(libraryId, batchMediaIds)) {
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

        LinkedHashMap<String, DeduplicatedFeedEntry> deduplicatedEntries = new LinkedHashMap<>();
        for (MediaEntity media : mediaBatch) {
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

        // R2-E-1: 跨页去重 - 跳过已展示的 deduplicationKey
        List<DeduplicatedFeedEntry> allDedupEntries = new ArrayList<>();
        for (DeduplicatedFeedEntry entry : deduplicatedEntries.values()) {
            String deduplicationKey = feedDeduplicationKey(entry.representativeMedia());
            if (seenDedupKeys.contains(deduplicationKey)) {
                continue;
            }
            allDedupEntries.add(entry);
        }

        boolean hasMoreInBatch = allDedupEntries.size() > normalizedPageSize;
        List<DeduplicatedFeedEntry> pageEntries = hasMoreInBatch
                ? allDedupEntries.subList(0, normalizedPageSize)
                : allDedupEntries;

        List<MediaDto> results = new ArrayList<>();
        Set<String> returnedDedupKeys = new LinkedHashSet<>();
        for (DeduplicatedFeedEntry entry : pageEntries) {
            results.add(contentMapper.toMediaDto(entry.representativeMedia(), entry.visiblePostIds()));
            returnedDedupKeys.add(feedDeduplicationKey(entry.representativeMedia()));
        }

        boolean hasMore = hasMoreInBatch || mediaBatch.size() >= fetchLimit;
        String nextCursor = null;
        if (hasMore && !results.isEmpty()) {
            MediaDto lastItem = results.get(results.size() - 1);
            // R2-E-1: nextCursor 携带已展示的 deduplicationKey, 用于下一页跨页去重
            Set<String> mergedSeenKeys = mergeSeenDedupKeys(seenDedupKeys, returnedDedupKeys);
            nextCursor = encodeCursor(lastItem.displayTimeMillis(), lastItem.mediaId(), mergedSeenKeys);
        }

        return new MediaFeedPage(results, nextCursor, hasMore, normalizedPageSize);
    }

    public List<MediaImportStatusDto> getImportStatus(AuthenticatedUser currentUser, List<String> sourceFingerprints) {
        String libraryId = currentUser.libraryId();
        List<String> normalizedFingerprints = sourceFingerprints.stream()
                .filter(fingerprint -> fingerprint != null && !fingerprint.isBlank())
                .map(fingerprint -> fingerprint.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalizedFingerprints.isEmpty()) {
            return List.of();
        }

        List<MediaEntity> mediaItems = mediaRepository.findByLibraryIdAndSourceFingerprintInAndDeletedAtIsNullAndDomainNotLife(
                libraryId,
                normalizedFingerprints
        );
        if (mediaItems.isEmpty()) {
            return List.of();
        }

        Map<String, MediaEntity> mediaById = mediaItems.stream()
                .collect(Collectors.toMap(MediaEntity::getId, media -> media));
        Set<String> activePostIds = postRepository
                .findByLibraryIdAndDeletedAtIsNullOrderByDisplayTimeMillisDescUpdatedAtDesc(libraryId)
                .stream()
                .map(PostEntity::getId)
                .collect(Collectors.toSet());
        Map<String, List<String>> smallAlbumIdsByMediaId = new LinkedHashMap<>();
        for (PostMediaEntity relation : postMediaRepository.findByLibraryIdAndMediaIdIn(libraryId, mediaById.keySet())) {
            if (!activePostIds.contains(relation.getPostId())) {
                continue;
            }
            smallAlbumIdsByMediaId.computeIfAbsent(relation.getMediaId(), ignored -> new ArrayList<>());
            List<String> smallAlbumIds = smallAlbumIdsByMediaId.get(relation.getMediaId());
            if (!smallAlbumIds.contains(relation.getPostId())) {
                smallAlbumIds.add(relation.getPostId());
            }
        }

        return mediaItems.stream()
                .filter(media -> media.getSourceFingerprint() != null && !media.getSourceFingerprint().isBlank())
                .map(media -> new MediaImportStatusDto(
                        media.getSourceFingerprint(),
                        media.getId(),
                        smallAlbumIdsByMediaId.getOrDefault(media.getId(), List.of())
                ))
                .toList();
    }

    /**
     * 修改媒体显示时间。仅记录所有者可修改。
     * 服务端将 displayTimeSource 置为 "MANUAL"，标识用户手动修改。
     * 不发 push 通知（与 LifeConsoleService.updateMediaLocation 一致）：
     * 1. 时间修改对其他端不是高优先级事件，依赖 SyncService 版本号涨触发轮询刷新即可
     * 2. 避免照片流和 life 模块互相干扰
     */
    @Transactional
    public Long updateMediaTime(String mediaId, Long displayTimeMillis, AuthenticatedUser currentUser) {
        MediaEntity media = mediaRepository.findByIdAndLibraryIdAndDeletedAtIsNull(mediaId, currentUser.libraryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Media was not found."));
        if (!currentUser.userId().equals(media.getRecordOwnerUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "You can only update your own media time.");
        }
        if (displayTimeMillis == null || displayTimeMillis <= 0L) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "displayTimeMillis must be a positive value.");
        }
        media.setDisplayTimeMillis(displayTimeMillis);
        media.setDisplayTimeSource("MANUAL");
        mediaRepository.save(media);
        // BaseEntity.@PreUpdate 会自动 bump updatedAt，推动 SyncService 的 photoFeedVersion / lifeConsoleVersion 涨
        return media.getDisplayTimeMillis();
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

    private String encodeCursor(long displayTimeMillis, String mediaId, Set<String> seenDedupKeys) {
        StringBuilder payload = new StringBuilder();
        payload.append(displayTimeMillis).append('|').append(mediaId);
        if (seenDedupKeys != null && !seenDedupKeys.isEmpty()) {
            for (String key : seenDedupKeys) {
                payload.append('\n').append(key);
            }
        }
        return cursorCodec.encode(payload.toString());
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String payload = cursorCodec.decode(cursor);
            String[] lines = payload.split("\n", -1);
            if (lines.length == 0) {
                return null;
            }
            String[] head = lines[0].split("\\|", 2);
            if (head.length != 2 || head[1].isBlank()) {
                return null;
            }
            long displayTime = Long.parseLong(head[0]);
            String mediaId = head[1];
            Set<String> seenDedupKeys = new LinkedHashSet<>();
            for (int i = 1; i < lines.length; i++) {
                if (!lines[i].isEmpty()) {
                    seenDedupKeys.add(lines[i]);
                }
            }
            return new Cursor(displayTime, mediaId, seenDedupKeys);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Set<String> mergeSeenDedupKeys(Set<String> previous, Set<String> current) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        merged.addAll(current);
        merged.addAll(previous);
        if (merged.size() <= MAX_SEEN_DEDUP_KEYS) {
            return merged;
        }
        // Keep most recent keys (current page first, then previous), cap at MAX_SEEN_DEDUP_KEYS
        LinkedHashSet<String> capped = new LinkedHashSet<>();
        for (String key : merged) {
            if (capped.size() >= MAX_SEEN_DEDUP_KEYS) {
                break;
            }
            capped.add(key);
        }
        return capped;
    }

    private record Cursor(long displayTimeMillis, String mediaId, Set<String> seenDedupKeys) {
    }

    private record MediaResourceResolution(
            String storagePath,
            boolean previewGenerated,
            boolean coverGenerated
    ) {
    }
}
