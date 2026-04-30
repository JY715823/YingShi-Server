package com.yingshi.server.mapper;

import com.yingshi.server.domain.AlbumEntity;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.dto.content.AlbumDto;
import com.yingshi.server.dto.content.MediaDto;
import com.yingshi.server.dto.content.PostDetailDto;
import com.yingshi.server.dto.content.PostMediaDto;
import com.yingshi.server.dto.content.PostSummaryDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ContentMapper {

    public AlbumDto toAlbumDto(AlbumEntity album, long postCount) {
        return new AlbumDto(
                album.getId(),
                album.getTitle(),
                album.getSubtitle(),
                album.getCoverMediaId(),
                postCount
        );
    }

    public PostSummaryDto toPostSummaryDto(PostEntity post, List<String> albumIds, String coverMediaId, long mediaCount) {
        return new PostSummaryDto(
                post.getId(),
                post.getTitle(),
                post.getSummary(),
                post.getContributorLabel(),
                post.getDisplayTimeMillis(),
                albumIds,
                coverMediaId,
                mediaCount
        );
    }

    public PostDetailDto toPostDetailDto(
            PostEntity post,
            List<String> albumIds,
            String coverMediaId,
            long mediaCount,
            List<PostMediaDto> mediaItems
    ) {
        return new PostDetailDto(
                post.getId(),
                post.getTitle(),
                post.getSummary(),
                post.getContributorLabel(),
                post.getDisplayTimeMillis(),
                albumIds,
                coverMediaId,
                mediaCount,
                mediaItems
        );
    }

    public PostMediaDto toPostMediaDto(PostMediaEntity relation, MediaDto mediaDto, boolean isCover) {
        return new PostMediaDto(
                relation.getSortOrder(),
                isCover,
                mediaDto
        );
    }

    public MediaDto toMediaDto(MediaEntity media, List<String> postIds) {
        return new MediaDto(
                media.getId(),
                media.getMediaType().name().toLowerCase(Locale.ROOT),
                media.getUrl(),
                media.getPreviewUrl(),
                media.getOriginalUrl(),
                media.getVideoUrl(),
                media.getCoverUrl(),
                media.getMimeType(),
                media.getSizeBytes(),
                media.getWidth(),
                media.getHeight(),
                media.getAspectRatio(),
                media.getDurationMillis(),
                media.getDisplayTimeMillis(),
                postIds
        );
    }
}
