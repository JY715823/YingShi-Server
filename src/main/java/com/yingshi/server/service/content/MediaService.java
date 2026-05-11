package com.yingshi.server.service.content;

import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.dto.content.MediaDto;
import com.yingshi.server.dto.content.MediaFeedPage;
import com.yingshi.server.mapper.ContentMapper;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.service.upload.LocalMediaStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;

@Service
public class MediaService {

    private static final int DEFAULT_FEED_PAGE_SIZE = 60;
    private static final int MAX_FEED_PAGE_SIZE = 120;
    private final MediaRepository mediaRepository;
    private final PostMediaRepository postMediaRepository;
    private final PostRepository postRepository;
    private final ContentMapper contentMapper;
    private final LocalMediaStorageService localMediaStorageService;
    private static final int PREVIEW_MAX_DIMENSION = 1280;
    private static final int VIDEO_COVER_MAX_DIMENSION = 1280;

    public MediaService(
            MediaRepository mediaRepository,
            PostMediaRepository postMediaRepository,
            PostRepository postRepository,
            ContentMapper contentMapper,
            LocalMediaStorageService localMediaStorageService
    ) {
        this.mediaRepository = mediaRepository;
        this.postMediaRepository = postMediaRepository;
        this.postRepository = postRepository;
        this.contentMapper = contentMapper;
        this.localMediaStorageService = localMediaStorageService;
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

        Set<String> activePostIds = postRepository.findAll().stream()
                .filter(post -> libraryId.equals(post.getLibraryId()) && post.getDeletedAt() == null)
                .map(PostEntity::getId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        Map<String, List<String>> postIdsByMediaId = new LinkedHashMap<>();
        for (PostMediaEntity relation : postMediaRepository.findByLibraryIdAndMediaIdIn(
                libraryId,
                mediaItems.stream().map(MediaEntity::getId).toList()
        )) {
            if (!activePostIds.contains(relation.getPostId())) {
                continue;
            }
            postIdsByMediaId.computeIfAbsent(relation.getMediaId(), key -> new ArrayList<>());
            List<String> postIds = postIdsByMediaId.get(relation.getMediaId());
            if (!postIds.contains(relation.getPostId())) {
                postIds.add(relation.getPostId());
            }
        }

        List<MediaDto> results = new ArrayList<>();
        for (MediaEntity media : mediaItems) {
            List<String> postIds = postIdsByMediaId.getOrDefault(media.getId(), List.of());
            if (!isRenderableMedia(media, postIds)) {
                continue;
            }
            results.add(contentMapper.toMediaDto(media, postIds));
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

    public MediaFilePayload loadMediaFile(String mediaId, String variant, AuthenticatedUser currentUser) {
        MediaEntity media = mediaRepository.findByIdAndLibraryId(mediaId, currentUser.libraryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Media was not found."));
        if (media.getStoragePath() == null || media.getStoragePath().isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Local file is not available for this media.");
        }
        Resource resource = resolveMediaResource(media, variant);
        String mimeType = isJpegVariant(media, variant)
                ? "image/jpeg"
                : media.getMimeType();
        Long contentLength = null;
        Long lastModifiedMillis = null;
        try {
            contentLength = resource.contentLength();
        } catch (Exception ignored) {
        }
        try {
            lastModifiedMillis = resource.lastModified();
        } catch (Exception ignored) {
        }
        return new MediaFilePayload(resource, mimeType, contentLength, lastModifiedMillis);
    }

    private Resource resolveMediaResource(MediaEntity media, String variant) {
        if ("preview".equalsIgnoreCase(variant) && media.getMediaType() == com.yingshi.server.domain.MediaType.IMAGE) {
            try {
                return localMediaStorageService.loadPreview(media.getStoragePath(), media.getId(), PREVIEW_MAX_DIMENSION);
            } catch (ApiException exception) {
                return localMediaStorageService.load(media.getStoragePath());
            }
        }
        if (("cover".equalsIgnoreCase(variant) || "preview".equalsIgnoreCase(variant)) &&
                media.getMediaType() == com.yingshi.server.domain.MediaType.VIDEO) {
            return localMediaStorageService.loadVideoCover(media.getStoragePath(), media.getId(), VIDEO_COVER_MAX_DIMENSION);
        }
        return localMediaStorageService.load(media.getStoragePath());
    }

    private boolean isJpegVariant(MediaEntity media, String variant) {
        if (media.getMediaType() == com.yingshi.server.domain.MediaType.IMAGE) {
            return "preview".equalsIgnoreCase(variant);
        }
        return media.getMediaType() == com.yingshi.server.domain.MediaType.VIDEO &&
                ("preview".equalsIgnoreCase(variant) || "cover".equalsIgnoreCase(variant));
    }

    private boolean isRenderableMedia(MediaEntity media, List<String> postIds) {
        return true;
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
}
