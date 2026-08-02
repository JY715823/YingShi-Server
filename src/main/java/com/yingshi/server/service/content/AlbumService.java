package com.yingshi.server.service.content;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.cursor.CursorCodec;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.AlbumEntity;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.dto.content.AlbumDto;
import com.yingshi.server.dto.content.CreateAlbumRequest;
import com.yingshi.server.dto.content.CursorPage;
import com.yingshi.server.dto.content.MoveSmallAlbumsRequest;
import com.yingshi.server.dto.content.PostSummaryDto;
import com.yingshi.server.dto.content.UpdateAlbumRequest;
import com.yingshi.server.dto.trash.TrashItemDto;
import com.yingshi.server.mapper.ContentMapper;
import com.yingshi.server.repository.AlbumRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.service.trash.TrashService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AlbumService {

    private static final int DEFAULT_LIST_PAGE_SIZE = 30;
    private static final int MAX_LIST_PAGE_SIZE = 100;
    private static final int LIST_OVER_FETCH_MARGIN = 1;

    private final AlbumRepository albumRepository;
    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final ContentMapper contentMapper;
    private final TrashService trashService;
    private final CursorCodec cursorCodec;

    public AlbumService(
            AlbumRepository albumRepository,
            PostRepository postRepository,
            PostMediaRepository postMediaRepository,
            ContentMapper contentMapper,
            TrashService trashService,
            CursorCodec cursorCodec
    ) {
        this.albumRepository = albumRepository;
        this.postRepository = postRepository;
        this.postMediaRepository = postMediaRepository;
        this.contentMapper = contentMapper;
        this.trashService = trashService;
        this.cursorCodec = cursorCodec;
    }

    @Transactional
    public AlbumDto createAlbum(CreateAlbumRequest request, AuthenticatedUser currentUser) {
        AlbumEntity album = new AlbumEntity();
        album.setId(IdGenerator.newId("album"));
        album.setLibraryId(currentUser.libraryId());
        album.setTitle(request.title().trim());
        album.setSubtitle(request.subtitle() == null ? "" : request.subtitle().trim());
        album.setCoverMediaId(null);
        albumRepository.save(album);
        return contentMapper.toAlbumDto(album, 0L);
    }

    @Transactional(readOnly = true)
    public CursorPage<AlbumDto> listAlbums(AuthenticatedUser currentUser, String cursor, Integer pageSize) {
        String libraryId = currentUser.libraryId();
        int normalizedPageSize = normalizeListPageSize(pageSize);
        String[] cursorParts = decodeListCursor(cursor);
        Instant cursorUpdatedAt = cursorParts != null ? Instant.ofEpochMilli(Long.parseLong(cursorParts[0])) : null;
        String cursorId = cursorParts != null ? cursorParts[1] : null;

        int fetchLimit = normalizedPageSize + LIST_OVER_FETCH_MARGIN;
        org.springframework.data.domain.Pageable pageRequest = PageRequest.of(0, fetchLimit, Sort.by(
                Sort.Order.desc("updatedAt"),
                Sort.Order.desc("id")
        ));
        List<AlbumEntity> albums = cursorUpdatedAt != null
                ? albumRepository.findAlbumPage(libraryId, cursorUpdatedAt, cursorId, pageRequest)
                : albumRepository.findAlbumFirstPage(libraryId, pageRequest);

        if (albums.isEmpty()) {
            return new CursorPage<>(List.of(), null, false, normalizedPageSize);
        }

        Map<String, Long> postCountByAlbumId = postRepository.countActivePostsGroupByAlbumId(libraryId)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));

        boolean hasMore = albums.size() > normalizedPageSize;
        List<AlbumEntity> pageAlbums = hasMore ? albums.subList(0, normalizedPageSize) : albums;

        List<AlbumDto> results = new ArrayList<>();
        for (AlbumEntity album : pageAlbums) {
            results.add(contentMapper.toAlbumDto(album, postCountByAlbumId.getOrDefault(album.getId(), 0L)));
        }

        String nextCursor = null;
        if (hasMore && !pageAlbums.isEmpty()) {
            AlbumEntity last = pageAlbums.get(pageAlbums.size() - 1);
            nextCursor = encodeListCursor(last.getUpdatedAt(), last.getId());
        }

        return new CursorPage<>(results, nextCursor, hasMore, normalizedPageSize);
    }

    @Transactional(readOnly = true)
    public CursorPage<PostSummaryDto> listAlbumPosts(String albumId, AuthenticatedUser currentUser, String cursor, Integer pageSize) {
        String libraryId = currentUser.libraryId();
        requireAlbum(albumId, libraryId);
        int normalizedPageSize = normalizeListPageSize(pageSize);
        String[] cursorParts = decodeListCursor(cursor);
        Instant cursorUpdatedAt = cursorParts != null ? Instant.ofEpochMilli(Long.parseLong(cursorParts[0])) : null;
        String cursorId = cursorParts != null ? cursorParts[1] : null;

        int fetchLimit = normalizedPageSize + LIST_OVER_FETCH_MARGIN;
        var pageable = PageRequest.of(0, fetchLimit, Sort.by(
                Sort.Order.desc("updatedAt"),
                Sort.Order.desc("id")
        ));
        List<PostEntity> posts = (cursorUpdatedAt != null)
                ? postRepository.findPostPageByAlbumNext(libraryId, albumId, cursorUpdatedAt, cursorId, pageable)
                : postRepository.findPostPageByAlbumFirst(libraryId, albumId, pageable);
        if (posts.isEmpty()) {
            return new CursorPage<>(List.of(), null, false, normalizedPageSize);
        }

        boolean hasMore = posts.size() > normalizedPageSize;
        List<PostEntity> pagePosts = hasMore ? posts.subList(0, normalizedPageSize) : posts;

        List<String> postIds = pagePosts.stream().map(PostEntity::getId).toList();
        Map<String, Long> mediaCountByPostId = postMediaRepository.findByLibraryIdAndPostIdIn(libraryId, postIds)
                .stream()
                .collect(Collectors.groupingBy(PostMediaEntity::getPostId, Collectors.counting()));

        List<PostSummaryDto> results = new ArrayList<>();
        for (PostEntity post : pagePosts) {
            results.add(contentMapper.toPostSummaryDto(
                    post,
                    post.getAlbumId(),
                    post.getCoverMediaId(),
                    mediaCountByPostId.getOrDefault(post.getId(), 0L)
            ));
        }

        String nextCursor = null;
        if (hasMore && !pagePosts.isEmpty()) {
            PostEntity last = pagePosts.get(pagePosts.size() - 1);
            nextCursor = encodeListCursor(last.getUpdatedAt(), last.getId());
        }

        return new CursorPage<>(results, nextCursor, hasMore, normalizedPageSize);
    }

    @Transactional
    public AlbumDto updateAlbum(String albumId, UpdateAlbumRequest request, AuthenticatedUser currentUser) {
        AlbumEntity album = requireAlbum(albumId, currentUser.libraryId());
        album.setTitle(request.title().trim());
        album.setSubtitle(request.subtitle() == null ? "" : request.subtitle().trim());
        albumRepository.save(album);
        long smallAlbumCount = postRepository.countByLibraryIdAndAlbumIdAndDeletedAtIsNull(
                currentUser.libraryId(),
                album.getId()
        );
        return contentMapper.toAlbumDto(album, smallAlbumCount);
    }

    @Transactional
    public TrashItemDto deleteAlbum(String albumId, AuthenticatedUser currentUser) {
        return trashService.deleteLargeAlbum(albumId, currentUser);
    }

    @Transactional
    public List<PostSummaryDto> moveSmallAlbums(
            String targetAlbumId,
            MoveSmallAlbumsRequest request,
            AuthenticatedUser currentUser
    ) {
        String libraryId = currentUser.libraryId();
        // 1. 校验目标大相册存在且属于当前 library
        requireAlbum(targetAlbumId, libraryId);

        // 2. 过滤 null/blank 的 smallAlbumId + 去重
        List<String> smallAlbumIds = request.smallAlbumIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        if (smallAlbumIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "smallAlbumIds must not be empty");
        }

        // 3. 批量查询待移动的小相册（校验存在 + 未删除 + 属于同一 library）
        List<PostEntity> smallAlbums = postRepository
                .findByLibraryIdAndIdInAndDeletedAtIsNull(libraryId, smallAlbumIds);

        if (smallAlbums.size() != smallAlbumIds.size()) {
            List<String> foundIds = smallAlbums.stream().map(PostEntity::getId).toList();
            List<String> missingIds = smallAlbumIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SMALL_ALBUM_NOT_FOUND,
                    "Small albums not found: " + missingIds);
        }

        // 4. 逐条更新 albumId + 记录修改人 + touch updatedAt
        for (PostEntity post : smallAlbums) {
            post.setAlbumId(targetAlbumId);
            post.setLastModifiedByUserId(currentUser.userId());
            post.touch();
        }
        postRepository.saveAll(smallAlbums);

        // 5. 转换为 PostSummaryDto 列表返回
        List<String> postIds = smallAlbums.stream().map(PostEntity::getId).toList();
        Map<String, Long> mediaCountByPostId = postMediaRepository
                .findByLibraryIdAndPostIdIn(libraryId, postIds)
                .stream()
                .collect(Collectors.groupingBy(PostMediaEntity::getPostId, Collectors.counting()));

        List<PostSummaryDto> results = new ArrayList<>();
        for (PostEntity post : smallAlbums) {
            results.add(contentMapper.toPostSummaryDto(
                    post,
                    post.getAlbumId(),
                    post.getCoverMediaId(),
                    mediaCountByPostId.getOrDefault(post.getId(), 0L)
            ));
        }
        return results;
    }

    private AlbumEntity requireAlbum(String albumId, String libraryId) {
        return albumRepository.findByIdAndLibraryIdAndDeletedAtIsNull(albumId, libraryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ALBUM_NOT_FOUND, "Album was not found."));
    }

    private int normalizeListPageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_LIST_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_LIST_PAGE_SIZE);
    }

    private String encodeListCursor(Instant updatedAt, String id) {
        String payload = updatedAt.toEpochMilli() + "|" + id;
        return cursorCodec.encode(payload);
    }

    private String[] decodeListCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String payload = cursorCodec.decode(cursor);
            String[] parts = payload.split("\\|", 2);
            if (parts.length != 2 || parts[1].isBlank()) {
                return null;
            }
            Long.parseLong(parts[0]);
            return parts;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
