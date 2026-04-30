package com.yingshi.server.service.content;

import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.dto.content.MediaDto;
import com.yingshi.server.mapper.ContentMapper;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.service.upload.LocalMediaStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MediaService {

    private final MediaRepository mediaRepository;
    private final PostMediaRepository postMediaRepository;
    private final PostRepository postRepository;
    private final ContentMapper contentMapper;
    private final LocalMediaStorageService localMediaStorageService;

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
        String spaceId = currentUser.spaceId();
        List<MediaEntity> mediaItems = mediaRepository.findBySpaceIdAndDeletedAtIsNull(spaceId)
                .stream()
                .sorted(Comparator.comparing(MediaEntity::getDisplayTimeMillis).reversed().thenComparing(MediaEntity::getId))
                .toList();
        if (mediaItems.isEmpty()) {
            return List.of();
        }

        Set<String> activePostIds = postRepository.findAll().stream()
                .filter(post -> spaceId.equals(post.getSpaceId()) && post.getDeletedAt() == null)
                .map(PostEntity::getId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        Map<String, List<String>> postIdsByMediaId = new LinkedHashMap<>();
        for (PostMediaEntity relation : postMediaRepository.findBySpaceIdAndMediaIdIn(
                spaceId,
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
            results.add(contentMapper.toMediaDto(media, postIdsByMediaId.getOrDefault(media.getId(), List.of())));
        }
        return results;
    }

    public MediaFilePayload loadMediaFile(String mediaId, AuthenticatedUser currentUser) {
        MediaEntity media = mediaRepository.findByIdAndSpaceIdAndDeletedAtIsNull(mediaId, currentUser.spaceId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Media was not found."));
        if (media.getStoragePath() == null || media.getStoragePath().startsWith("seed/")) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Local file is not available for seeded media.");
        }
        Resource resource = localMediaStorageService.load(media.getStoragePath());
        return new MediaFilePayload(resource, media.getMimeType());
    }
}
