package com.yingshi.server.service.trash;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import com.yingshi.server.repository.CommentRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.repository.TrashItemRepository;
import com.yingshi.server.service.upload.LocalMediaStorageService;
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
import java.util.Map;
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
    private final CommentRepository commentRepository;
    private final TrashMapper trashMapper;
    private final LocalMediaStorageService localMediaStorageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TrashService(
            TrashItemRepository trashItemRepository,
            PostRepository postRepository,
            MediaRepository mediaRepository,
            PostMediaRepository postMediaRepository,
            CommentRepository commentRepository,
            TrashMapper trashMapper,
            LocalMediaStorageService localMediaStorageService
    ) {
        this.trashItemRepository = trashItemRepository;
        this.postRepository = postRepository;
        this.mediaRepository = mediaRepository;
        this.postMediaRepository = postMediaRepository;
        this.commentRepository = commentRepository;
        this.trashMapper = trashMapper;
        this.localMediaStorageService = localMediaStorageService;
    }

    @Transactional
    public TrashItemDto deleteSmallAlbum(String smallAlbumId, AuthenticatedUser currentUser) {
        PostEntity smallAlbum = requireActiveSmallAlbum(smallAlbumId, currentUser.libraryId());
        smallAlbum.setDeletedAt(Instant.now());
        postRepository.save(smallAlbum);

        List<String> mediaIds = postMediaRepository.findByLibraryIdAndPostIdOrderBySortOrderAsc(currentUser.libraryId(), smallAlbumId)
                .stream()
                .map(PostMediaEntity::getMediaId)
                .distinct()
                .toList();

        TrashItemEntity item = createTrashItem(
                currentUser.libraryId(),
                currentUser.userId(),
                TrashItemType.SMALL_ALBUM_DELETED,
                smallAlbumId,
                null,
                smallAlbum.getTitle(),
                "Small album deleted",
                List.of(smallAlbumId),
                mediaIds,
                new SmallAlbumDeletedSnapshot(smallAlbumId)
        );
        return toTrashItemDto(item);
    }

    @Transactional
    public TrashItemDto deleteSmallAlbumMedia(
            String smallAlbumId,
            String mediaId,
            PostMediaDeleteMode deleteMode,
            AuthenticatedUser currentUser
    ) {
        PostEntity smallAlbum = requireActiveSmallAlbum(smallAlbumId, currentUser.libraryId());
        PostMediaEntity relation = requireRelation(currentUser.libraryId(), smallAlbumId, mediaId);
        if (deleteMode == PostMediaDeleteMode.SYSTEM) {
            return systemDeleteMediaInternal(mediaId, currentUser, Optional.of(smallAlbum.getId()), true);
        }

        MediaEntity media = requireActiveMedia(mediaId, currentUser.libraryId());
        boolean wasCover = mediaId.equals(smallAlbum.getCoverMediaId());
        int sortOrder = relation.getSortOrder();
        postMediaRepository.delete(relation);
        resequenceSmallAlbumMedia(currentUser.libraryId(), smallAlbumId);
        if (wasCover) {
            smallAlbum.setCoverMediaId(resolveFirstVisibleMediaId(currentUser.libraryId(), smallAlbumId).orElse(null));
            postRepository.save(smallAlbum);
        }

        TrashItemEntity item = createTrashItem(
                currentUser.libraryId(),
                currentUser.userId(),
                TrashItemType.MEDIA_REMOVED,
                smallAlbumId,
                mediaId,
                smallAlbum.getTitle(),
                "Media removed from small album",
                List.of(smallAlbumId),
                List.of(mediaId),
                new MediaRemovedSnapshot(smallAlbumId, mediaId, sortOrder, wasCover)
        );
        return toTrashItemDto(item);
    }

    @Transactional
    public TrashItemDto systemDeleteMedia(String mediaId, AuthenticatedUser currentUser) {
        return systemDeleteMediaInternal(mediaId, currentUser, Optional.empty(), true);
    }

    @Transactional
    public TrashItemDto systemDeleteMediaAllowingEmptySmallAlbums(String mediaId, AuthenticatedUser currentUser) {
        return systemDeleteMediaInternal(mediaId, currentUser, Optional.empty(), true);
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
                ? trashItemRepository.findByLibraryIdAndState(currentUser.libraryId(), TrashItemState.IN_TRASH, pageRequest)
                : trashItemRepository.findByLibraryIdAndStateAndItemType(
                currentUser.libraryId(),
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
        TrashItemEntity item = requireTrashItem(trashItemId, currentUser.libraryId());
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
        TrashItemEntity item = requireTrashItem(trashItemId, currentUser.libraryId());
        if (item.getState() != TrashItemState.IN_TRASH) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.RESTORE_CONFLICT, "Only in-trash items can be restored.");
        }

        switch (item.getItemType()) {
            case SMALL_ALBUM_DELETED -> restoreSmallAlbumDeleted(item, currentUser.libraryId());
            case MEDIA_REMOVED -> restoreMediaRemoved(item, currentUser.libraryId());
            case MEDIA_SYSTEM_DELETED -> restoreMediaSystemDeleted(item, currentUser.libraryId());
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.RESTORE_CONFLICT, "Unsupported trash item type.");
        }

        item.setState(TrashItemState.RESTORED);
        item.setRestoredAt(Instant.now());
        trashItemRepository.save(item);
        return toTrashItemDto(item);
    }

    @Transactional
    public PendingCleanupDto moveOutOfTrash(String trashItemId, AuthenticatedUser currentUser) {
        TrashItemEntity item = requireTrashItem(trashItemId, currentUser.libraryId());
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
    public TrashItemDto purgeTrashItem(String trashItemId, AuthenticatedUser currentUser) {
        TrashItemEntity item = requireTrashItem(trashItemId, currentUser.libraryId());
        if (item.getState() != TrashItemState.IN_TRASH) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.REMOVE_FROM_TRASH_CONFLICT, "Only in-trash items can be permanently deleted.");
        }

        TrashItemDto itemDto = toTrashItemDto(item);
        purgeItemData(item, currentUser.libraryId());
        trashItemRepository.delete(item);
        return itemDto;
    }

    @Transactional
    public void purgeExpiredPendingCleanupItem(String trashItemId, String libraryId) {
        TrashItemEntity item = requireTrashItem(trashItemId, libraryId);
        if (item.getState() != TrashItemState.PENDING_CLEANUP) {
            return;
        }
        if (item.getUndoDeadlineAt() == null || item.getUndoDeadlineAt().isAfter(Instant.now())) {
            return;
        }
        purgeItemData(item, libraryId);
        trashItemRepository.delete(item);
    }

    @Transactional
    public TrashItemDto undoRemove(String trashItemId, AuthenticatedUser currentUser) {
        TrashItemEntity item = requireTrashItem(trashItemId, currentUser.libraryId());
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
        return trashItemRepository.findByLibraryIdAndStateOrderByDeletedAtDesc(currentUser.libraryId(), TrashItemState.PENDING_CLEANUP)
                .stream()
                .map(item -> trashMapper.toPendingCleanupDto(toTrashItemDto(item), item.getRemovedAt(), item.getUndoDeadlineAt()))
                .toList();
    }

    private TrashItemDto systemDeleteMediaInternal(
            String mediaId,
            AuthenticatedUser currentUser,
            Optional<String> requestedSmallAlbumId,
            boolean allowEmptySmallAlbums
    ) {
        MediaEntity media = requireActiveMedia(mediaId, currentUser.libraryId());
        List<PostMediaEntity> relations = postMediaRepository.findByLibraryIdAndMediaIdIn(currentUser.libraryId(), List.of(mediaId));
        if (requestedSmallAlbumId.isPresent() && relations.stream().noneMatch(relation -> relation.getPostId().equals(requestedSmallAlbumId.get()))) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Media was not found in the current small album.");
        }

        List<String> relatedSmallAlbumIds = relations.stream()
                .map(PostMediaEntity::getPostId)
                .distinct()
                .toList();
        List<MediaSystemRelationSnapshot> relationSnapshots = relations.stream()
                .map(relation -> new MediaSystemRelationSnapshot(relation.getPostId(), relation.getSortOrder()))
                .toList();

        List<PostEntity> smallAlbums = relatedSmallAlbumIds.isEmpty()
                ? List.of()
                : postRepository.findByLibraryIdAndIdIn(currentUser.libraryId(), relatedSmallAlbumIds);
        if (!allowEmptySmallAlbums) {
            for (String relatedSmallAlbumId : relatedSmallAlbumIds) {
                assertSmallAlbumKeepsVisibleMedia(
                        currentUser.libraryId(),
                        relatedSmallAlbumId,
                        Set.of(mediaId)
                );
            }
        }
        Set<String> coverSmallAlbumIds = smallAlbums.stream()
                .filter(smallAlbum -> mediaId.equals(smallAlbum.getCoverMediaId()))
                .map(PostEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        postMediaRepository.deleteAll(relations);
        for (String relatedSmallAlbumId : relatedSmallAlbumIds) {
            resequenceSmallAlbumMedia(currentUser.libraryId(), relatedSmallAlbumId);
        }

        media.setDeletedAt(Instant.now());
        mediaRepository.save(media);

        for (PostEntity smallAlbum : smallAlbums) {
            if (mediaId.equals(smallAlbum.getCoverMediaId())) {
                smallAlbum.setCoverMediaId(resolveFirstVisibleMediaId(currentUser.libraryId(), smallAlbum.getId(), mediaId).orElse(null));
                postRepository.save(smallAlbum);
            }
        }

        TrashItemEntity item = createTrashItem(
                currentUser.libraryId(),
                currentUser.userId(),
                TrashItemType.MEDIA_SYSTEM_DELETED,
                requestedSmallAlbumId.orElse(null),
                mediaId,
                media.getId(),
                "Media system deleted",
                relatedSmallAlbumIds,
                List.of(mediaId),
                new MediaSystemDeletedSnapshot(mediaId, relationSnapshots, new ArrayList<>(coverSmallAlbumIds))
        );
        return toTrashItemDto(item);
    }

    private void restoreSmallAlbumDeleted(TrashItemEntity item, String libraryId) {
        SmallAlbumDeletedSnapshot snapshot = readSmallAlbumDeletedSnapshot(item);
        PostEntity smallAlbum = postRepository.findByIdAndLibraryId(snapshot.smallAlbumId(), libraryId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, ErrorCode.RESTORE_CONFLICT, "Small album can no longer be restored."));
        smallAlbum.setDeletedAt(null);
        postRepository.save(smallAlbum);
    }

    private void restoreMediaRemoved(TrashItemEntity item, String libraryId) {
        MediaRemovedSnapshot snapshot = readMediaRemovedSnapshot(item);
        PostEntity smallAlbum = postRepository.findByIdAndLibraryIdAndDeletedAtIsNull(snapshot.smallAlbumId(), libraryId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, ErrorCode.RESTORE_CONFLICT, "原小相册不可用，无法恢复到原小相册"));
        requireActiveMedia(snapshot.mediaId(), libraryId);
        if (!postMediaRepository.existsByLibraryIdAndPostIdAndMediaId(libraryId, snapshot.smallAlbumId(), snapshot.mediaId())) {
            restoreRelationOrder(libraryId, snapshot.smallAlbumId(), snapshot.mediaId(), snapshot.sortOrder());
        }
        if (snapshot.wasCover()) {
            smallAlbum.setCoverMediaId(snapshot.mediaId());
            postRepository.save(smallAlbum);
        }
        resequenceSmallAlbumMedia(libraryId, snapshot.smallAlbumId());
    }

    private void restoreMediaSystemDeleted(TrashItemEntity item, String libraryId) {
        MediaSystemDeletedSnapshot snapshot = readSnapshot(item.getSnapshotJson(), MediaSystemDeletedSnapshot.class);
        MediaEntity media = mediaRepository.findByIdAndLibraryId(snapshot.mediaId(), libraryId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, ErrorCode.RESTORE_CONFLICT, "Media can no longer be restored."));
        media.setDeletedAt(null);
        mediaRepository.save(media);

        for (MediaSystemRelationSnapshot relationSnapshot : snapshot.relations()) {
            postRepository.findByIdAndLibraryIdAndDeletedAtIsNull(relationSnapshot.postId(), libraryId)
                    .ifPresent(post -> {
                        if (!postMediaRepository.existsByLibraryIdAndPostIdAndMediaId(libraryId, relationSnapshot.postId(), snapshot.mediaId())) {
                            restoreRelationOrder(libraryId, relationSnapshot.postId(), snapshot.mediaId(), relationSnapshot.sortOrder());
                        }
                    });
        }

        for (String postId : snapshot.coverPostIds()) {
            postRepository.findByIdAndLibraryIdAndDeletedAtIsNull(postId, libraryId)
                    .ifPresent(post -> {
                        if (post.getCoverMediaId() == null && postMediaRepository.existsByLibraryIdAndPostIdAndMediaId(libraryId, postId, snapshot.mediaId())) {
                            post.setCoverMediaId(snapshot.mediaId());
                            postRepository.save(post);
                        }
                    });
        }
    }

    private void purgeSmallAlbumDeleted(TrashItemEntity item, String libraryId) {
        SmallAlbumDeletedSnapshot snapshot = readSmallAlbumDeletedSnapshot(item);
        purgeMediaRemovedItemsForSmallAlbum(libraryId, snapshot.smallAlbumId());
        postMediaRepository.deleteByLibraryIdAndPostId(libraryId, snapshot.smallAlbumId());
        commentRepository.deleteByLibraryIdAndPostId(libraryId, snapshot.smallAlbumId());
        postRepository.findByIdAndLibraryId(snapshot.smallAlbumId(), libraryId).ifPresent(postRepository::delete);
    }

    private void purgeMediaRemoved(TrashItemEntity item) {
        // Removing a media-from-post trash record only makes that relation deletion final.
        // The media file itself remains App content and may still appear in the photo feed or other posts.
    }

    private void purgeMediaSystemDeleted(TrashItemEntity item, String libraryId) {
        MediaSystemDeletedSnapshot snapshot = readMediaSystemDeletedSnapshot(item);
        MediaEntity media = mediaRepository.findByIdAndLibraryId(snapshot.mediaId(), libraryId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, ErrorCode.REMOVE_FROM_TRASH_CONFLICT, "Media can no longer be permanently deleted."));

        localMediaStorageService.deleteStoredMediaFiles(media.getStoragePath(), media.getId());
        postMediaRepository.deleteByLibraryIdAndMediaId(libraryId, media.getId());
        commentRepository.deleteByLibraryIdAndMediaId(libraryId, media.getId());
        mediaRepository.delete(media);
    }

    private void purgeMediaRemovedItemsForSmallAlbum(String libraryId, String smallAlbumId) {
        List<TrashItemEntity> mediaRemovedItems = trashItemRepository.findByLibraryIdAndStateAndItemType(
                libraryId,
                TrashItemState.IN_TRASH,
                TrashItemType.MEDIA_REMOVED
        );
        trashItemRepository.deleteAll(mediaRemovedItems.stream()
                .filter(mediaRemovedItem -> smallAlbumId.equals(mediaRemovedItem.getSourcePostId()))
                .toList());
    }

    private void purgeItemData(TrashItemEntity item, String libraryId) {
        switch (item.getItemType()) {
            case SMALL_ALBUM_DELETED -> purgeSmallAlbumDeleted(item, libraryId);
            case MEDIA_REMOVED -> purgeMediaRemoved(item);
            case MEDIA_SYSTEM_DELETED -> purgeMediaSystemDeleted(item, libraryId);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.REMOVE_FROM_TRASH_CONFLICT, "Unsupported trash item type.");
        }
    }

    private TrashItemEntity createTrashItem(
            String libraryId,
            String actorUserId,
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
        item.setLibraryId(libraryId);
        item.setItemType(itemType);
        item.setState(TrashItemState.IN_TRASH);
        item.setActorUserId(actorUserId);
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

    private void restoreRelationOrder(String libraryId, String postId, String mediaId, int sortOrder) {
        int restoredSortOrder = Math.max(1, sortOrder);
        List<PostMediaEntity> relations = postMediaRepository.findByLibraryIdAndPostIdOrderBySortOrderAsc(libraryId, postId);
        for (PostMediaEntity relation : relations) {
            if (relation.getSortOrder() >= restoredSortOrder) {
                relation.setSortOrder(relation.getSortOrder() + 1000);
            }
        }
        postMediaRepository.saveAll(relations);
        postMediaRepository.flush();

        for (PostMediaEntity relation : relations) {
            if (relation.getSortOrder() >= restoredSortOrder + 1000) {
                relation.setSortOrder(relation.getSortOrder() - 999);
            }
        }
        postMediaRepository.saveAll(relations);
        postMediaRepository.flush();

        PostMediaEntity restoredRelation = new PostMediaEntity();
        restoredRelation.setId(IdGenerator.newId("small_album_media"));
        restoredRelation.setLibraryId(libraryId);
        restoredRelation.setPostId(postId);
        restoredRelation.setMediaId(mediaId);
        restoredRelation.setSortOrder(restoredSortOrder);
        postMediaRepository.save(restoredRelation);
    }

    private TrashItemEntity requireTrashItem(String trashItemId, String libraryId) {
        return trashItemRepository.findByIdAndLibraryId(trashItemId, libraryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.TRASH_ITEM_NOT_FOUND, "Trash item was not found."));
    }

    private PostEntity requireActiveSmallAlbum(String smallAlbumId, String libraryId) {
        return postRepository.findByIdAndLibraryIdAndDeletedAtIsNull(smallAlbumId, libraryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SMALL_ALBUM_NOT_FOUND, "Small album was not found."));
    }

    private MediaEntity requireActiveMedia(String mediaId, String libraryId) {
        return mediaRepository.findByIdAndLibraryIdAndDeletedAtIsNull(mediaId, libraryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Media was not found."));
    }

    private PostMediaEntity requireRelation(String libraryId, String postId, String mediaId) {
        return postMediaRepository.findByLibraryIdAndPostIdOrderBySortOrderAsc(libraryId, postId)
                .stream()
                .filter(relation -> relation.getMediaId().equals(mediaId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Media was not found in the current post."));
    }

    private void assertSmallAlbumKeepsVisibleMedia(String libraryId, String smallAlbumId, Set<String> removedMediaIds) {
        Optional<PostEntity> maybeSmallAlbum = postRepository.findByIdAndLibraryId(smallAlbumId, libraryId);
        if (maybeSmallAlbum.isEmpty() || maybeSmallAlbum.get().getDeletedAt() != null) {
            return;
        }

        long remainingVisibleMediaCount = postMediaRepository.findByLibraryIdAndPostIdOrderBySortOrderAsc(libraryId, smallAlbumId)
                .stream()
                .map(PostMediaEntity::getMediaId)
                .distinct()
                .filter(mediaId -> !removedMediaIds.contains(mediaId))
                .count();
        if (remainingVisibleMediaCount <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DELETE_CONFLICT, "This action would leave the small album empty; delete the whole small album or keep at least one media item.");
        }
    }

    private void resequenceSmallAlbumMedia(String libraryId, String smallAlbumId) {
        List<PostMediaEntity> relations = postMediaRepository.findByLibraryIdAndPostIdOrderBySortOrderAsc(libraryId, smallAlbumId);
        for (int i = 0; i < relations.size(); i++) {
            relations.get(i).setSortOrder(i + 1);
        }
        postMediaRepository.saveAll(relations);
    }

    private Optional<String> resolveFirstVisibleMediaId(String libraryId, String postId) {
        return resolveFirstVisibleMediaId(libraryId, postId, null);
    }

    private Optional<String> resolveFirstVisibleMediaId(String libraryId, String postId, String excludedMediaId) {
        List<PostMediaEntity> relations = postMediaRepository.findByLibraryIdAndPostIdOrderBySortOrderAsc(libraryId, postId);
        for (PostMediaEntity relation : relations) {
            if (excludedMediaId != null && excludedMediaId.equals(relation.getMediaId())) {
                continue;
            }
            if (mediaRepository.findByIdAndLibraryIdAndDeletedAtIsNull(relation.getMediaId(), libraryId).isPresent()) {
                return Optional.of(relation.getMediaId());
            }
        }
        return Optional.empty();
    }

    private TrashItemDto toTrashItemDto(TrashItemEntity item) {
        MediaEntity sourceMedia = item.getSourceMediaId() == null || item.getSourceMediaId().isBlank()
                ? null
                : mediaRepository.findByIdAndLibraryId(item.getSourceMediaId(), item.getLibraryId()).orElse(null);
        return trashMapper.toTrashItemDto(
                item,
                splitIds(item.getRelatedPostIds()),
                splitIds(item.getRelatedMediaIds()),
                sourceMedia
        );
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

    private SmallAlbumDeletedSnapshot readSmallAlbumDeletedSnapshot(TrashItemEntity item) {
        String snapshotJson = normalizeLegacySnapshotJson(item);
        try {
            Map<String, Object> snapshot = objectMapper.readValue(snapshotJson, new TypeReference<>() {
            });
            String smallAlbumId = readString(snapshot, "smallAlbumId");
            if (smallAlbumId == null || smallAlbumId.isBlank()) {
                smallAlbumId = readString(snapshot, "postId");
            }
            if (smallAlbumId == null || smallAlbumId.isBlank()) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Trash snapshot is missing small album id.");
            }
            return new SmallAlbumDeletedSnapshot(smallAlbumId);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Failed to read trash snapshot.");
        }
    }

    private MediaRemovedSnapshot readMediaRemovedSnapshot(TrashItemEntity item) {
        String snapshotJson = normalizeLegacySnapshotJson(item);
        try {
            Map<String, Object> snapshot = objectMapper.readValue(snapshotJson, new TypeReference<>() {
            });
            String smallAlbumId = readString(snapshot, "smallAlbumId");
            if (smallAlbumId == null || smallAlbumId.isBlank()) {
                smallAlbumId = readString(snapshot, "postId");
            }
            String mediaId = readString(snapshot, "mediaId");
            int sortOrder = readInt(snapshot, "sortOrder", 1);
            boolean wasCover = readBoolean(snapshot, "wasCover");
            if (smallAlbumId == null || smallAlbumId.isBlank() || mediaId == null || mediaId.isBlank()) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Trash snapshot is missing media restore fields.");
            }
            return new MediaRemovedSnapshot(smallAlbumId, mediaId, sortOrder, wasCover);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Failed to read trash snapshot.");
        }
    }

    private MediaSystemDeletedSnapshot readMediaSystemDeletedSnapshot(TrashItemEntity item) {
        return readSnapshot(normalizeLegacySnapshotJson(item), MediaSystemDeletedSnapshot.class);
    }

    private String normalizeLegacySnapshotJson(TrashItemEntity item) {
        String snapshotJson = item.getSnapshotJson();
        if (snapshotJson == null || snapshotJson.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Trash snapshot is empty.");
        }
        if (!snapshotJson.matches("\\d+")) {
            return snapshotJson;
        }
        String smallAlbumId = item.getSourcePostId();
        if (smallAlbumId == null || smallAlbumId.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Trash snapshot is missing small album id.");
        }
        try {
            return objectMapper.writeValueAsString(Map.of("smallAlbumId", smallAlbumId));
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Failed to normalize trash snapshot.");
        }
    }

    private String readString(Map<String, Object> snapshot, String key) {
        Object value = snapshot.get(key);
        if (value instanceof String stringValue) {
            return stringValue;
        }
        return value == null ? null : String.valueOf(value);
    }

    private int readInt(Map<String, Object> snapshot, String key, int defaultValue) {
        Object value = snapshot.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean readBoolean(Map<String, Object> snapshot, String key) {
        Object value = snapshot.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return false;
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

    private record SmallAlbumDeletedSnapshot(String smallAlbumId) {
    }

    private record MediaRemovedSnapshot(String smallAlbumId, String mediaId, int sortOrder, boolean wasCover) {
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
