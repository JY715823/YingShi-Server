package com.yingshi.server.service.content;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.AlbumEntity;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.PostAlbumEntity;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.dto.content.AddPostMediaRequest;
import com.yingshi.server.dto.content.CreatePostRequest;
import com.yingshi.server.dto.content.MediaDto;
import com.yingshi.server.dto.content.PostDetailDto;
import com.yingshi.server.dto.content.PostMediaDto;
import com.yingshi.server.dto.content.UpdatePostCoverRequest;
import com.yingshi.server.dto.content.UpdatePostMediaOrderRequest;
import com.yingshi.server.dto.content.UpdatePostRequest;
import com.yingshi.server.mapper.ContentMapper;
import com.yingshi.server.repository.AlbumRepository;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.PostAlbumRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PostService {

    private static final String DEFAULT_CONTRIBUTOR_LABEL = "You and Me";

    private final PostRepository postRepository;
    private final MediaRepository mediaRepository;
    private final AlbumRepository albumRepository;
    private final PostMediaRepository postMediaRepository;
    private final PostAlbumRepository postAlbumRepository;
    private final ContentMapper contentMapper;

    public PostService(
            PostRepository postRepository,
            MediaRepository mediaRepository,
            AlbumRepository albumRepository,
            PostMediaRepository postMediaRepository,
            PostAlbumRepository postAlbumRepository,
            ContentMapper contentMapper
    ) {
        this.postRepository = postRepository;
        this.mediaRepository = mediaRepository;
        this.albumRepository = albumRepository;
        this.postMediaRepository = postMediaRepository;
        this.postAlbumRepository = postAlbumRepository;
        this.contentMapper = contentMapper;
    }

    @Transactional(readOnly = true)
    public PostDetailDto getPostDetail(String postId, AuthenticatedUser currentUser) {
        PostEntity post = requirePost(postId, currentUser.spaceId());
        return buildPostDetail(post);
    }

    @Transactional
    public PostDetailDto createPost(CreatePostRequest request, AuthenticatedUser currentUser) {
        String spaceId = currentUser.spaceId();
        validateDistinctIds(request.albumIds(), ErrorCode.ALBUM_ASSIGNMENT_INVALID, "albumIds contains duplicates.");
        validateDistinctIds(request.initialMediaIds(), ErrorCode.POST_MEDIA_ORDER_INVALID, "initialMediaIds contains duplicates.");

        List<AlbumEntity> albums = requireAlbums(spaceId, request.albumIds());
        Map<String, MediaEntity> mediaById = requireMedia(spaceId, request.initialMediaIds());
        String coverMediaId = resolveCoverMediaId(request.coverMediaId(), request.initialMediaIds());

        PostEntity post = new PostEntity();
        post.setId(IdGenerator.newId("post"));
        post.setSpaceId(spaceId);
        post.setTitle(request.title().trim());
        post.setSummary(request.summary());
        post.setContributorLabel(normalizeContributorLabel(request.contributorLabel()));
        post.setDisplayTimeMillis(request.displayTimeMillis());
        post.setCoverMediaId(coverMediaId);
        postRepository.save(post);

        savePostMediaRelations(post.getId(), spaceId, request.initialMediaIds());
        savePostAlbumRelations(post.getId(), spaceId, albums.stream().map(AlbumEntity::getId).toList());
        return buildPostDetail(post);
    }

    @Transactional
    public PostDetailDto updatePost(String postId, UpdatePostRequest request, AuthenticatedUser currentUser) {
        String spaceId = currentUser.spaceId();
        PostEntity post = requirePost(postId, spaceId);

        if (request.title() != null) {
            post.setTitle(request.title().trim());
        }
        if (request.summary() != null) {
            post.setSummary(request.summary());
        }
        if (request.contributorLabel() != null) {
            post.setContributorLabel(normalizeContributorLabel(request.contributorLabel()));
        }
        if (request.displayTimeMillis() != null) {
            post.setDisplayTimeMillis(request.displayTimeMillis());
        }

        if (request.albumIds() != null) {
            validateDistinctIds(request.albumIds(), ErrorCode.ALBUM_ASSIGNMENT_INVALID, "albumIds contains duplicates.");
            if (request.albumIds().isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.ALBUM_ASSIGNMENT_INVALID, "albumIds must not be empty.");
            }
            List<AlbumEntity> albums = requireAlbums(spaceId, request.albumIds());
            postAlbumRepository.deleteAll(postAlbumRepository.findBySpaceIdAndPostId(spaceId, postId));
            savePostAlbumRelations(postId, spaceId, albums.stream().map(AlbumEntity::getId).toList());
        }

        postRepository.save(post);
        return buildPostDetail(post);
    }

    @Transactional
    public PostDetailDto addMediaToPost(String postId, AddPostMediaRequest request, AuthenticatedUser currentUser) {
        String spaceId = currentUser.spaceId();
        PostEntity post = requirePost(postId, spaceId);
        validateDistinctIds(request.mediaIds(), ErrorCode.POST_MEDIA_ORDER_INVALID, "mediaIds contains duplicates.");
        Map<String, MediaEntity> mediaById = requireMedia(spaceId, request.mediaIds());

        List<PostMediaEntity> existingRelations = postMediaRepository.findBySpaceIdAndPostIdOrderBySortOrderAsc(spaceId, postId);
        Set<String> existingMediaIds = existingRelations.stream().map(PostMediaEntity::getMediaId).collect(Collectors.toSet());
        for (String mediaId : request.mediaIds()) {
            if (existingMediaIds.contains(mediaId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.POST_MEDIA_ORDER_INVALID, "mediaIds must not include media already attached to the post.");
            }
            if (!mediaById.containsKey(mediaId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.MEDIA_NOT_FOUND, "One or more mediaIds do not exist in the current space.");
            }
        }

        List<String> orderedMediaIds = new ArrayList<>(existingRelations.stream().map(PostMediaEntity::getMediaId).toList());
        orderedMediaIds.addAll(request.mediaIds());
        postMediaRepository.deleteAll(existingRelations);
        postMediaRepository.flush();
        savePostMediaRelations(postId, spaceId, orderedMediaIds);

        if (request.coverMediaId() != null && !request.coverMediaId().isBlank()) {
            if (!orderedMediaIds.contains(request.coverMediaId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.POST_COVER_INVALID, "coverMediaId must belong to the post.");
            }
            post.setCoverMediaId(request.coverMediaId());
            postRepository.save(post);
        } else if (post.getCoverMediaId() == null) {
            post.setCoverMediaId(orderedMediaIds.get(0));
            postRepository.save(post);
        }

        return buildPostDetail(post);
    }

    @Transactional
    public PostDetailDto updatePostCover(String postId, UpdatePostCoverRequest request, AuthenticatedUser currentUser) {
        String spaceId = currentUser.spaceId();
        PostEntity post = requirePost(postId, spaceId);
        if (!postMediaRepository.existsBySpaceIdAndPostIdAndMediaId(spaceId, postId, request.coverMediaId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.POST_COVER_INVALID, "Cover media must belong to the current post.");
        }

        post.setCoverMediaId(request.coverMediaId());
        postRepository.save(post);
        return buildPostDetail(post);
    }

    @Transactional
    public PostDetailDto updatePostMediaOrder(String postId, UpdatePostMediaOrderRequest request, AuthenticatedUser currentUser) {
        String spaceId = currentUser.spaceId();
        PostEntity post = requirePost(postId, spaceId);
        List<PostMediaEntity> relations = postMediaRepository.findBySpaceIdAndPostIdOrderBySortOrderAsc(spaceId, postId);
        List<String> currentMediaIds = relations.stream().map(PostMediaEntity::getMediaId).toList();
        List<String> orderedMediaIds = request.orderedMediaIds();

        validateDistinctIds(orderedMediaIds, ErrorCode.POST_MEDIA_ORDER_INVALID, "orderedMediaIds contains duplicates.");
        if (relations.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.POST_MEDIA_ORDER_INVALID, "Post has no media to reorder.");
        }
        if (orderedMediaIds.size() != currentMediaIds.size() || !new HashSet<>(orderedMediaIds).equals(new HashSet<>(currentMediaIds))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.POST_MEDIA_ORDER_INVALID, "orderedMediaIds must match the current post media set.");
        }

        postMediaRepository.deleteAll(relations);
        postMediaRepository.flush();
        savePostMediaRelations(postId, spaceId, orderedMediaIds);

        if (post.getCoverMediaId() == null) {
            post.setCoverMediaId(orderedMediaIds.get(0));
            postRepository.save(post);
        }
        return buildPostDetail(post);
    }

    private PostEntity requirePost(String postId, String spaceId) {
        return postRepository.findByIdAndSpaceIdAndDeletedAtIsNull(postId, spaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.POST_NOT_FOUND, "Post was not found."));
    }

    private List<AlbumEntity> requireAlbums(String spaceId, Collection<String> albumIds) {
        List<AlbumEntity> albums = albumRepository.findBySpaceIdAndIdIn(spaceId, albumIds);
        if (albums.size() != albumIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.ALBUM_ASSIGNMENT_INVALID, "One or more albumIds do not exist in the current space.");
        }
        return albums;
    }

    private Map<String, MediaEntity> requireMedia(String spaceId, Collection<String> mediaIds) {
        List<MediaEntity> mediaItems = mediaRepository.findBySpaceIdAndIdInAndDeletedAtIsNull(spaceId, mediaIds);
        if (mediaItems.size() != mediaIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.MEDIA_NOT_FOUND, "One or more mediaIds do not exist in the current space.");
        }
        return mediaItems.stream().collect(Collectors.toMap(MediaEntity::getId, media -> media));
    }

    private String resolveCoverMediaId(String coverMediaId, List<String> mediaIds) {
        if (coverMediaId == null || coverMediaId.isBlank()) {
            return mediaIds.get(0);
        }
        if (!mediaIds.contains(coverMediaId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.POST_COVER_INVALID, "coverMediaId must exist in initialMediaIds.");
        }
        return coverMediaId;
    }

    private String normalizeContributorLabel(String contributorLabel) {
        if (contributorLabel == null || contributorLabel.isBlank()) {
            return DEFAULT_CONTRIBUTOR_LABEL;
        }
        return contributorLabel.trim();
    }

    private void savePostMediaRelations(String postId, String spaceId, List<String> mediaIds) {
        List<PostMediaEntity> relations = new ArrayList<>();
        for (int i = 0; i < mediaIds.size(); i++) {
            PostMediaEntity relation = new PostMediaEntity();
            relation.setId(IdGenerator.newId("post_media"));
            relation.setSpaceId(spaceId);
            relation.setPostId(postId);
            relation.setMediaId(mediaIds.get(i));
            relation.setSortOrder(i + 1);
            relations.add(relation);
        }
        postMediaRepository.saveAll(relations);
    }

    private void savePostAlbumRelations(String postId, String spaceId, List<String> albumIds) {
        List<PostAlbumEntity> relations = new ArrayList<>();
        for (String albumId : albumIds) {
            PostAlbumEntity relation = new PostAlbumEntity();
            relation.setId(IdGenerator.newId("post_album"));
            relation.setSpaceId(spaceId);
            relation.setPostId(postId);
            relation.setAlbumId(albumId);
            relations.add(relation);
        }
        postAlbumRepository.saveAll(relations);
    }

    private void validateDistinctIds(List<String> ids, ErrorCode errorCode, String message) {
        if (ids.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    private PostDetailDto buildPostDetail(PostEntity post) {
        String spaceId = post.getSpaceId();
        List<PostAlbumEntity> albumRelations = postAlbumRepository.findBySpaceIdAndPostId(spaceId, post.getId());
        List<String> albumIds = albumRelations.stream()
                .map(PostAlbumEntity::getAlbumId)
                .sorted(String::compareTo)
                .toList();

        List<PostMediaEntity> mediaRelations = postMediaRepository.findBySpaceIdAndPostIdOrderBySortOrderAsc(spaceId, post.getId());
        List<String> mediaIds = mediaRelations.stream().map(PostMediaEntity::getMediaId).toList();
        Map<String, MediaEntity> mediaById = mediaIds.isEmpty()
                ? Map.of()
                : mediaRepository.findBySpaceIdAndIdInAndDeletedAtIsNull(spaceId, mediaIds)
                .stream()
                .collect(Collectors.toMap(MediaEntity::getId, media -> media));

        List<PostMediaDto> mediaItems = new ArrayList<>();
        for (PostMediaEntity relation : mediaRelations) {
            MediaEntity media = mediaById.get(relation.getMediaId());
            if (media == null) {
                continue;
            }
            MediaDto mediaDto = contentMapper.toMediaDto(media, null);
            mediaItems.add(contentMapper.toPostMediaDto(relation, mediaDto, relation.getMediaId().equals(post.getCoverMediaId())));
        }

        String resolvedCoverMediaId = mediaItems.stream()
                .filter(PostMediaDto::isCover)
                .map(item -> item.media().mediaId())
                .findFirst()
                .orElse(null);

        return contentMapper.toPostDetailDto(post, albumIds, resolvedCoverMediaId, mediaItems.size(), mediaItems);
    }
}
