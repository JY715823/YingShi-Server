package com.yingshi.server.service.trash;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaDeleteMode;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.domain.TrashItemEntity;
import com.yingshi.server.domain.TrashItemState;
import com.yingshi.server.domain.TrashItemType;
import com.yingshi.server.dto.trash.PendingCleanupDto;
import com.yingshi.server.dto.trash.TrashDetailDto;
import com.yingshi.server.dto.trash.TrashItemDto;
import com.yingshi.server.dto.trash.TrashPageResponse;
import com.yingshi.server.mapper.TrashMapper;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.repository.TrashItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TrashService {

    private static final Duration UNDO_WINDOW = Duration.ofHours(24);
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;

    private final TrashItemRepository trashItemRepository;
    private final PostRepository postRepository;
    private final MediaRepository mediaRepository;
    private final PostMediaRepository postMediaRepository;
    private final TrashMapper trashMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TrashService(
            TrashItemRepository trashItemRepository,
            PostRepository postRepository,
            MediaRepository mediaRepository,
            PostMediaRepository postMediaRepository,
            TrashMapper trashMapper
    ) {
        this.trashItemRepository = trashItemRepository;
        this.postRepository = postRepository;
        this.mediaRepository = mediaRepository;
        this.postMediaRepository = postMediaRepository;
        this.trashMapper = trashMapper;
    }

    @Transactional
    public TrashItemDto deletePost(String postId, AuthenticatedUser currentUser) {
        PostEntity post = requireActivePost(postId, currentUser.spaceId());
        post.setDeletedAt(Instant.now());
        postRepository.save(post);

        List<String> mediaIds = postMediaRepository.findBySpaceIdAndPostIdOrderBySortOrderAsc(currentUser.spaceId(), postId)
                .stream()
                .map(PostMediaEntity::getMediaId)
                .distinct()
                .toList();

        TrashItemEntity item = createTrashItem(
                currentUser.spaceId(),
                TrashItemType.POST_DELETED,
                postId,
                null,
                post.getTitle(),
                "Post deleted",
                List.of(postId),
                mediaIds,
                new PostDeletedSnapshot(postId)
        );
        return toTrashItemDto(item);
    }

    @Transactional
    public TrashItemDto deletePostMedia(
            String postId,
            String mediaId,
            PostMediaDeleteMode deleteMode,
            AuthenticatedUser currentUser
    ) {
        PostEntity post = requireActivePost(postId, currentUser.spaceId());
        PostMediaEntity relation = requireRelation(currentUser.spaceId(), postId, mediaId);
        if (deleteMode == PostMediaDeleteMode.SYSTEM) {
            return systemDeleteMediaInternal(mediaId, currentUser, Optional.of(post.getId()));
        }

        assertPostKeepsVisibleMedia(
                currentUser.spaceId(),
                postId,
                Set.of(mediaId)
        );

        MediaEntity media = requireActiveMedia(mediaId, currentUser.spaceId());
        boolean wasCover = mediaId.equals(post.getCoverMediaId());
        int sortOrder = relation.getSortOrder();
        postMediaRepository.delete(relation);
        resequencePostMedia(currentUser.spaceId(), postId);
        if (wasCover) {
            post.setCoverMediaId(resolveFirstVisibleMediaId(currentUser.spaceId(), postId).orElse(null));
            postRepository.save(post);
        }

        TrashItemEntity item = createTrashItem(
                currentUser.spaceId(),
                TrashItemType.MEDIA_REMOVED,
                postId,
                mediaId,
                post.getTitle(),
                "Media removed from post",
                List.of(postId),
                List.of(mediaId),
                new MediaRemovedSnapshot(postId, mediaId, sortOrder, wasCover)
        );
        return toTrashItemDto(item);
    }

    @Transactional
    public TrashItemDto systemDeleteMedia(String mediaId, AuthenticatedUser currentUser) {
        return systemDeleteMediaInternal(mediaId, currentUser, Optional.empty());
    }

    @Transactional(readOnly = true)
    public TrashPageResponse listTrash(String itemType, Integer page, Integer size, AuthenticatedUser currentUser) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        PageRequest pageRequest = PageRequest.of(
                normalizedPage - 1,
                normalizedSize,
                Sort.by(Sort.Order.desc("deletedAt"), Sort.Order.desc("id"))
        );

        Page<TrashItemEntity> items = itemType == null || itemType.isBlank()
                ? trashItemRepository.findBySpaceIdAndState(currentUser.spaceId(), TrashItemState.IN_TRASH, pageRequest)
                : trashItemRepository.findBySpaceIdAndStateAndItemType(
                currentUser.spaceId(),
                TrashItemState.IN_TRASH,
                parseItemType(itemType),
                pageRequest
        );

        return new TrashPageResponse(
                items.getContent().stream().map(this::toTrashItemDto).toList(),
                normalizedPage,
                normalizedSize,
                items.getTotalElements(),
                items.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public TrashDetailDto getTrashDetail(String trashItemId, AuthenticatedUser currentUser) {
        TrashItemEntity item = requireTrashItem(trashItemId, currentUser.spaceId());
        TrashItemDto itemDto = toTrashItemDto(item);
        PendingCleanupDto pendingCleanup = item.getState() == TrashItemState.PENDING_CLEANUP
                ? trashMapper.toPendingCleanupDto(itemDto, item.getRemovedAt(), item.getUndoDeadlineAt())
                : null;
        return trashMapper.toTrashDetailDto(
                itemDto,
                item.getState() == TrashItemState.IN_TRASH,
                item.getState() == TrashItemState.IN_TRASH,
                pendingCleanup
        );
    }

    @Transactional
    public TrashItemDto restoreTrashItem(String trashItemId, AuthenticatedUser currentUser) {
        TrashItemEntity item = requireTrashItem(trashItemId, currentUser.spaceId());
        if (item.getState() != TrashItemState.IN_TRASH) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.RESTORE_CONFLICT, "Only in-trash items can be restored.");
        }

        switch (item.getItemType()) {
            case POST_DELETED -> restorePostDeleted(item, currentUser.spaceId());
            case MEDIA_REMOVED -> restoreMediaRemoved(item, currentUser.spaceId());
            case MEDIA_SYSTEM_DELETED -> restoreMediaSystemDeleted(item, currentUser.spaceId());
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.RESTORE_CONFLICT, "Unsupported trash item type.");
        }

        item.setState(TrashItemState.RESTORED);
        item.setRestoredAt(Instant.now());
        trashItemRepository.save(item);
        return toTrashItemDto(item);
    }

    @Transactional
    public PendingCleanupDto moveOutOfTrash(String trashItemId, AuthenticatedUser currentUser) {
        TrashItemEntity item = requireTrashItem(trashItemId, currentUser.spaceId());
        if (item.getState() != TrashItemState.IN_TRASH) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.REMOVE_FROM_TRASH_CONFLICT, "Only in-trash items can be moved to pending cleanup.");
        }
        item.setState(TrashItemState.PENDING_CLEANUP);
        item.setRemovedAt(Instant.now());
        item.setUndoDeadlineAt(item.getRemovedAt().plus(UNDO_WINDOW));
        trashItemRepository.save(item);
        TrashItemDto itemDto = toTrashItemDto(item);
        return trashMapper.toPendingCleanupDto(itemDto, item.getRemovedAt(), item.getUndoDeadlineAt());
    }

    @Transactional
    public TrashItemDto undoRemove(String trashItemId, AuthenticatedUser currentUser) {
        TrashItemEntity item = requireTrashItem(trashItemId, currentUser.spaceId());
        if (item.getState() != TrashItemState.PENDING_CLEANUP) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.REMOVE_FROM_TRASH_CONFLICT, "Trash item is not pending cleanup.");
        }
        if (item.getUndoDeadlineAt() != null && item.getUndoDeadlineAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.UNDO_REMOVE_EXPIRED, "Undo window has expired.");
        }
        item.setState(TrashItemState.IN_TRASH);
        item.setRemovedAt(null);
        item.setUndoDeadlineAt(null);
        trashItemRepository.save(item);
        return toTrashItemDto(item);
    }

    @Transactional(readOnly = true)
    public List<PendingCleanupDto> getPendingCleanup(AuthenticatedUser currentUser) {
        return trashItemRepository.findBySpaceIdAndStateOrderByDeletedAtDesc(currentUser.spaceId(), TrashItemState.PENDING_CLEANUP)
                .stream()
                .map(item -> trashMapper.toPendingCleanupDto(toTrashItemDto(item), item.getRemovedAt(), item.getUndoDeadlineAt()))
                .toList();
    }

    private TrashItemDto systemDeleteMediaInternal(String mediaId, AuthenticatedUser currentUser, Optional<String> requestedPostId) {
        MediaEntity media = requireActiveMedia(mediaId, currentUser.spaceId());
        List<PostMediaEntity> relations = postMediaRepository.findBySpaceIdAndMediaIdIn(currentUser.spaceId(), List.of(mediaId));
        if (requestedPostId.isPresent() && relations.stream().noneMatch(relation -> relation.getPostId().equals(requestedPostId.get()))) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Media was not found in the current post.");
        }

        List<String> relatedPostIds = relations.stream()
                .map(PostMediaEntity::getPostId)
                .distinct()
                .toList();
        List<MediaSystemRelationSnapshot> relationSnapshots = relations.stream()
                .map(relation -> new MediaSystemRelationSnapshot(relation.getPostId(), relation.getSortOrder()))
                .toList();

        List<PostEntity> posts = relatedPostIds.isEmpty()
                ? List.of()
                : postRepository.findBySpaceIdAndIdIn(currentUser.spaceId(), relatedPostIds);
        for (String relatedPostId : relatedPostIds) {
            assertPostKeepsVisibleMedia(
                    currentUser.spaceId(),
                    relatedPostId,
                    Set.of(mediaId)
            );
        }
        Set<String> coverPostIds = posts.stream()
                .filter(post -> mediaId.equals(post.getCoverMediaId()))
                .map(PostEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        postMediaRepository.deleteAll(relations);
        for (String postId : relatedPostIds) {
            resequencePostMedia(currentUser.spaceId(), postId);
        }

        media.setDeletedAt(Instant.now());
        mediaRepository.save(media);

        for (PostEntity post : posts) {
            if (mediaId.equals(post.getCoverMediaId())) {
                post.setCoverMediaId(resolveFirstVisibleMediaId(currentUser.spaceId(), post.getId(), mediaId).orElse(null));
                postRepository.save(post);
            }
        }

        TrashItemEntity item = createTrashItem(
                currentUser.spaceId(),
                TrashItemType.MEDIA_SYSTEM_DELETED,
                requestedPostId.orElse(null),
                mediaId,
                media.getId(),
                "Media system deleted",
                relatedPostIds,
                List.of(mediaId),
                new MediaSystemDeletedSnapshot(mediaId, relationSnapshots, new ArrayList<>(coverPostIds))
        );
        return toTrashItemDto(item);
    }

    private void restorePostDeleted(TrashItemEntity item, String spaceId) {
        PostDeletedSnapshot snapshot = readSnapshot(item.getSnapshotJson(), PostDeletedSnapshot.class);
        PostEntity post = postRepository.findByIdAndSpaceId(snapshot.postId(), spaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, ErrorCode.RESTORE_CONFLICT, "Post can no longer be restored."));
        post.setDeletedAt(null);
        postRepository.save(post);
    }

    private void restoreMediaRemoved(TrashItemEntity item, String spaceId) {
        MediaRemovedSnapshot snapshot = readSnapshot(item.getSnapshotJson(), MediaRemovedSnapshot.class);
        PostEntity post = requireActivePost(snapshot.postId(), spaceId);
        requireActiveMedia(snapshot.mediaId(), spaceId);
        if (!postMediaRepository.existsBySpaceIdAndPostIdAndMediaId(spaceId, snapshot.postId(), snapshot.mediaId())) {
            restoreRelationOrder(spaceId, snapshot.postId(), snapshot.mediaId(), snapshot.sortOrder());
        }
        if (snapshot.wasCover()) {
            post.setCoverMediaId(snapshot.mediaId());
            postRepository.save(post);
        }
        resequencePostMedia(spaceId, snapshot.postId());
    }

    private void restoreMediaSystemDeleted(TrashItemEntity item, String spaceId) {
        MediaSystemDeletedSnapshot snapshot = readSnapshot(item.getSnapshotJson(), MediaSystemDeletedSnapshot.class);
        MediaEntity media = mediaRepository.findByIdAndSpaceId(snapshot.mediaId(), spaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, ErrorCode.RESTORE_CONFLICT, "Media can no longer be restored."));
        media.setDeletedAt(null);
        mediaRepository.save(media);

        for (MediaSystemRelationSnapshot relationSnapshot : snapshot.relations()) {
            postRepository.findByIdAndSpaceIdAndDeletedAtIsNull(relationSnapshot.postId(), spaceId)
                    .ifPresent(post -> {
                        if (!postMediaRepository.existsBySpaceIdAndPostIdAndMediaId(spaceId, relationSnapshot.postId(), snapshot.mediaId())) {
                            restoreRelationOrder(spaceId, relationSnapshot.postId(), snapshot.mediaId(), relationSnapshot.sortOrder());
                        }
                    });
        }

        for (String postId : snapshot.coverPostIds()) {
            postRepository.findByIdAndSpaceIdAndDeletedAtIsNull(postId, spaceId)
                    .ifPresent(post -> {
                        if (post.getCoverMediaId() == null && postMediaRepository.existsBySpaceIdAndPostIdAndMediaId(spaceId, postId, snapshot.mediaId())) {
                            post.setCoverMediaId(snapshot.mediaId());
                            postRepository.save(post);
                        }
                    });
        }
    }

    private TrashItemEntity createTrashItem(
            String spaceId,
            TrashItemType itemType,
            String sourcePostId,
            String sourceMediaId,
            String title,
            String previewInfo,
            List<String> relatedPostIds,
            List<String> relatedMediaIds,
            Object snapshot
    ) {
        TrashItemEntity item = new TrashItemEntity();
        item.setId(IdGenerator.newId("trash"));
        item.setSpaceId(spaceId);
        item.setItemType(itemType);
        item.setState(TrashItemState.IN_TRASH);
        item.setSourcePostId(sourcePostId);
        item.setSourceMediaId(sourceMediaId);
        item.setTitle(title);
        item.setPreviewInfo(previewInfo);
        item.setRelatedPostIds(String.join(",", relatedPostIds));
        item.setRelatedMediaIds(String.join(",", relatedMediaIds));
        item.setSnapshotJson(writeSnapshot(snapshot));
        item.setDeletedAt(Instant.now());
        return trashItemRepository.save(item);
    }

    private TrashItemEntity requireTrashItem(String trashItemId, String spaceId) {
        return trashItemRepository.findByIdAndSpaceId(trashItemId, spaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.TRASH_ITEM_NOT_FOUND, "Trash item was not found."));
    }

    private PostEntity requireActivePost(String postId, String spaceId) {
        return postRepository.findByIdAndSpaceIdAndDeletedAtIsNull(postId, spaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.POST_NOT_FOUND, "Post was not found."));
    }

    private MediaEntity requireActiveMedia(String mediaId, String spaceId) {
        return mediaRepository.findByIdAndSpaceIdAndDeletedAtIsNull(mediaId, spaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Media was not found."));
    }

    private PostMediaEntity requireRelation(String spaceId, String postId, String mediaId) {
        return postMediaRepository.findBySpaceIdAndPostIdOrderBySortOrderAsc(spaceId, postId)
                .stream()
                .filter(relation -> relation.getMediaId().equals(mediaId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Media was not found in the current post."));
    }

    private void assertPostKeepsVisibleMedia(String spaceId, String postId, Set<String> removedMediaIds) {
        Optional<PostEntity> maybePost = postRepository.findByIdAndSpaceId(postId, spaceId);
        if (maybePost.isEmpty() || maybePost.get().getDeletedAt() != null) {
            return;
        }

        long remainingVisibleMediaCount = postMediaRepository.findBySpaceIdAndPostIdOrderBySortOrderAsc(spaceId, postId)
                .stream()
                .map(PostMediaEntity::getMediaId)
                .distinct()
                .filter(mediaId -> !removedMediaIds.contains(mediaId))
                .count();
        if (remainingVisibleMediaCount <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.DELETE_CONFLICT,
                    "当前操作会让帖子变成空帖，请删除整个帖子或至少保留一张媒体。"
            );
        }
    }

    private void resequencePostMedia(String spaceId, String postId) {
        List<PostMediaEntity> relations = postMediaRepository.findBySpaceIdAndPostIdOrderBySortOrderAsc(spaceId, postId);
        for (int i = 0; i < relations.size(); i++) {
            relations.get(i).setSortOrder(i + 1);
        }
        postMediaRepository.saveAll(relations);
    }

    private void restoreRelationOrder(String spaceId, String postId, String mediaId, int sortOrder) {
        List<PostMediaEntity> existingRelations = postMediaRepository.findBySpaceIdAndPostIdOrderBySortOrderAsc(spaceId, postId);
        List<String> orderedMediaIds = existingRelations.stream()
                .map(PostMediaEntity::getMediaId)
                .collect(Collectors.toCollection(ArrayList::new));
        int insertIndex = Math.max(0, Math.min(sortOrder - 1, orderedMediaIds.size()));
        orderedMediaIds.add(insertIndex, mediaId);

        postMediaRepository.deleteAll(existingRelations);
        postMediaRepository.flush();
        savePostMediaRelations(spaceId, postId, orderedMediaIds);
    }

    private void savePostMediaRelations(String spaceId, String postId, List<String> mediaIds) {
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

    private Optional<String> resolveFirstVisibleMediaId(String spaceId, String postId) {
        return resolveFirstVisibleMediaId(spaceId, postId, null);
    }

    private Optional<String> resolveFirstVisibleMediaId(String spaceId, String postId, String excludedMediaId) {
        List<PostMediaEntity> relations = postMediaRepository.findBySpaceIdAndPostIdOrderBySortOrderAsc(spaceId, postId);
        for (PostMediaEntity relation : relations) {
            if (excludedMediaId != null && excludedMediaId.equals(relation.getMediaId())) {
                continue;
            }
            if (mediaRepository.findByIdAndSpaceIdAndDeletedAtIsNull(relation.getMediaId(), spaceId).isPresent()) {
                return Optional.of(relation.getMediaId());
            }
        }
        return Optional.empty();
    }

    private TrashItemDto toTrashItemDto(TrashItemEntity item) {
        return trashMapper.toTrashItemDto(item, splitIds(item.getRelatedPostIds()), splitIds(item.getRelatedMediaIds()));
    }

    private List<String> splitIds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .filter(part -> !part.isBlank())
                .toList();
    }

    private String writeSnapshot(Object snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Failed to serialize trash snapshot.");
        }
    }

    private <T> T readSnapshot(String snapshotJson, Class<T> type) {
        try {
            return objectMapper.readValue(snapshotJson, type);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Failed to read trash snapshot.");
        }
    }

    private TrashItemType parseItemType(String value) {
        String normalized = value.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
        try {
            return TrashItemType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Unsupported trash item type.");
        }
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, 100);
    }

    private record PostDeletedSnapshot(String postId) {
    }

    private record MediaRemovedSnapshot(String postId, String mediaId, int sortOrder, boolean wasCover) {
    }

    private record MediaSystemDeletedSnapshot(
            String mediaId,
            List<MediaSystemRelationSnapshot> relations,
            List<String> coverPostIds
    ) {
    }

    private record MediaSystemRelationSnapshot(String postId, int sortOrder) {
    }
}
