package com.yingshi.server.service.content;

import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.dto.content.MediaDto;
import com.yingshi.server.mapper.ContentMapper;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.PostMediaRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MediaService {

    private final MediaRepository mediaRepository;
    private final PostMediaRepository postMediaRepository;
    private final ContentMapper contentMapper;

    public MediaService(
            MediaRepository mediaRepository,
            PostMediaRepository postMediaRepository,
            ContentMapper contentMapper
    ) {
        this.mediaRepository = mediaRepository;
        this.postMediaRepository = postMediaRepository;
        this.contentMapper = contentMapper;
    }

    public List<MediaDto> getMediaFeed(AuthenticatedUser currentUser) {
        String spaceId = currentUser.spaceId();
        List<MediaEntity> mediaItems = mediaRepository.findBySpaceId(spaceId)
                .stream()
                .sorted(Comparator.comparing(MediaEntity::getDisplayTimeMillis).reversed().thenComparing(MediaEntity::getId))
                .toList();

        Map<String, List<String>> postIdsByMediaId = new LinkedHashMap<>();
        for (PostMediaEntity relation : postMediaRepository.findBySpaceIdAndMediaIdIn(
                spaceId,
                mediaItems.stream().map(MediaEntity::getId).toList()
        )) {
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
}
