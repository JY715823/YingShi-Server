package com.yingshi.server.service.content;

import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.AlbumEntity;
import com.yingshi.server.domain.PostAlbumEntity;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.dto.content.AlbumDto;
import com.yingshi.server.dto.content.PostSummaryDto;
import com.yingshi.server.mapper.ContentMapper;
import com.yingshi.server.repository.AlbumRepository;
import com.yingshi.server.repository.PostAlbumRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final PostAlbumRepository postAlbumRepository;
    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final ContentMapper contentMapper;

    public AlbumService(
            AlbumRepository albumRepository,
            PostAlbumRepository postAlbumRepository,
            PostRepository postRepository,
            PostMediaRepository postMediaRepository,
            ContentMapper contentMapper
    ) {
        this.albumRepository = albumRepository;
        this.postAlbumRepository = postAlbumRepository;
        this.postRepository = postRepository;
        this.postMediaRepository = postMediaRepository;
        this.contentMapper = contentMapper;
    }

    public List<AlbumDto> listAlbums(AuthenticatedUser currentUser) {
        String spaceId = currentUser.spaceId();
        List<AlbumEntity> albums = albumRepository.findBySpaceIdOrderByTitleAsc(spaceId);
        Map<String, Long> postCountByAlbumId = postAlbumRepository.findAll()
                .stream()
                .filter(relation -> spaceId.equals(relation.getSpaceId()))
                .collect(Collectors.groupingBy(PostAlbumEntity::getAlbumId, Collectors.counting()));

        List<AlbumDto> results = new ArrayList<>();
        for (AlbumEntity album : albums) {
            results.add(contentMapper.toAlbumDto(album, postCountByAlbumId.getOrDefault(album.getId(), 0L)));
        }
        return results;
    }

    public List<PostSummaryDto> listAlbumPosts(String albumId, AuthenticatedUser currentUser) {
        String spaceId = currentUser.spaceId();
        requireAlbum(albumId, spaceId);

        List<PostAlbumEntity> albumRelations = postAlbumRepository.findBySpaceIdAndAlbumId(spaceId, albumId);
        Set<String> postIds = albumRelations.stream().map(PostAlbumEntity::getPostId).collect(Collectors.toSet());
        if (postIds.isEmpty()) {
            return List.of();
        }

        List<PostEntity> posts = postRepository.findBySpaceIdAndIdIn(spaceId, postIds)
                .stream()
                .sorted(Comparator.comparing(PostEntity::getDisplayTimeMillis).reversed().thenComparing(PostEntity::getId))
                .toList();

        Map<String, List<String>> albumIdsByPostId = groupAlbumIdsByPostId(postAlbumRepository.findBySpaceIdAndPostIdIn(spaceId, postIds));
        Map<String, Long> mediaCountByPostId = postMediaRepository.findBySpaceIdAndPostIdIn(spaceId, postIds)
                .stream()
                .collect(Collectors.groupingBy(PostMediaEntity::getPostId, Collectors.counting()));

        List<PostSummaryDto> results = new ArrayList<>();
        for (PostEntity post : posts) {
            results.add(contentMapper.toPostSummaryDto(
                    post,
                    albumIdsByPostId.getOrDefault(post.getId(), List.of()),
                    mediaCountByPostId.getOrDefault(post.getId(), 0L)
            ));
        }
        return results;
    }

    private void requireAlbum(String albumId, String spaceId) {
        albumRepository.findByIdAndSpaceId(albumId, spaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ALBUM_NOT_FOUND, "Album was not found."));
    }

    private Map<String, List<String>> groupAlbumIdsByPostId(List<PostAlbumEntity> relations) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (PostAlbumEntity relation : relations) {
            grouped.computeIfAbsent(relation.getPostId(), key -> new ArrayList<>()).add(relation.getAlbumId());
        }
        return grouped;
    }
}
