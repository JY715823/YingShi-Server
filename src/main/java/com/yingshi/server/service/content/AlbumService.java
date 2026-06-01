package com.yingshi.server.service.content;

import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.AlbumEntity;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.dto.content.AlbumDto;
import com.yingshi.server.dto.content.PostSummaryDto;
import com.yingshi.server.mapper.ContentMapper;
import com.yingshi.server.repository.AlbumRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final ContentMapper contentMapper;

    public AlbumService(
            AlbumRepository albumRepository,
            PostRepository postRepository,
            PostMediaRepository postMediaRepository,
            ContentMapper contentMapper
    ) {
        this.albumRepository = albumRepository;
        this.postRepository = postRepository;
        this.postMediaRepository = postMediaRepository;
        this.contentMapper = contentMapper;
    }

    public List<AlbumDto> listAlbums(AuthenticatedUser currentUser) {
        String libraryId = currentUser.libraryId();
        List<AlbumEntity> albums = albumRepository.findByLibraryIdOrderByTitleAsc(libraryId);
        Map<String, Long> postCountByAlbumId = postRepository
                .findByLibraryIdAndDeletedAtIsNullOrderByDisplayTimeMillisDescUpdatedAtDesc(libraryId)
                .stream()
                .collect(Collectors.groupingBy(PostEntity::getAlbumId, Collectors.counting()));

        List<AlbumDto> results = new ArrayList<>();
        for (AlbumEntity album : albums) {
            results.add(contentMapper.toAlbumDto(album, postCountByAlbumId.getOrDefault(album.getId(), 0L)));
        }
        return results;
    }

    public List<PostSummaryDto> listAlbumPosts(String albumId, AuthenticatedUser currentUser) {
        String libraryId = currentUser.libraryId();
        requireAlbum(albumId, libraryId);

        List<PostEntity> posts = postRepository.findByLibraryIdAndAlbumIdAndDeletedAtIsNullOrderByDisplayTimeMillisDescUpdatedAtDesc(
                libraryId,
                albumId
        );
        if (posts.isEmpty()) {
            return List.of();
        }

        List<String> postIds = posts.stream().map(PostEntity::getId).toList();
        Map<String, Long> mediaCountByPostId = postMediaRepository.findByLibraryIdAndPostIdIn(libraryId, postIds)
                .stream()
                .collect(Collectors.groupingBy(PostMediaEntity::getPostId, Collectors.counting()));

        List<PostSummaryDto> results = new ArrayList<>();
        for (PostEntity post : posts) {
            results.add(contentMapper.toPostSummaryDto(
                    post,
                    post.getAlbumId(),
                    post.getCoverMediaId(),
                    mediaCountByPostId.getOrDefault(post.getId(), 0L)
            ));
        }
        return results;
    }

    private void requireAlbum(String albumId, String libraryId) {
        albumRepository.findByIdAndLibraryId(albumId, libraryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ALBUM_NOT_FOUND, "Album was not found."));
    }
}
