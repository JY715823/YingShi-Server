package com.yingshi.server.mapper;

import com.yingshi.server.domain.AlbumEntity;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.dto.content.AlbumDto;
import com.yingshi.server.dto.content.MediaDto;
import com.yingshi.server.dto.content.PostDetailDto;
import com.yingshi.server.dto.content.PostMediaDto;
import com.yingshi.server.dto.content.PostSummaryDto;
import com.yingshi.server.service.storage.ObjectKeyPolicy;
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
                post.getEventStartedAtMillis(),
                post.getEventEndedAtMillis(),
                post.getDisplayTimeSource(),
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
                post.getEventStartedAtMillis(),
                post.getEventEndedAtMillis(),
                post.getDisplayTimeSource(),
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
        String localMediaUrl = localMediaUrl(media);
        String previewMediaUrl = previewMediaUrl(media);
        return new MediaDto(
                media.getId(),
                media.getMediaType().name().toLowerCase(Locale.ROOT),
                localMediaUrl != null ? localMediaUrl : media.getUrl(),
                previewMediaUrl != null ? previewMediaUrl : media.getPreviewUrl(),
                media.getMediaType() == MediaType.IMAGE && localMediaUrl != null ? localMediaUrl : media.getOriginalUrl(),
                media.getMediaType() == MediaType.VIDEO && localMediaUrl != null ? localMediaUrl : media.getVideoUrl(),
                media.getMediaType() == MediaType.VIDEO ? media.getCoverUrl() : null,
                media.getMimeType(),
                media.getSizeBytes(),
                media.getWidth(),
                media.getHeight(),
                media.getAspectRatio(),
                media.getDurationMillis(),
                media.getDisplayTimeMillis(),
                media.getCapturedAtMillis(),
                media.getImportedAtMillis(),
                media.getDisplayTimeSource(),
                postIds
        );
    }

    private String localMediaUrl(MediaEntity media) {
        String storagePath = media.getStoragePath();
        String originalObjectKey = media.getOriginalObjectKey();
        if ((storagePath == null || storagePath.isBlank())
                && !ObjectKeyPolicy.isRelativeObjectKey(originalObjectKey)) {
            return null;
        }
        return "/api/media/files/" + media.getId();
    }

    private String previewMediaUrl(MediaEntity media) {
        String localMediaUrl = localMediaUrl(media);
        if (localMediaUrl == null) {
            return null;
        }
        if (media.getMediaType() == MediaType.IMAGE) {
            return localMediaUrl + "?variant=preview";
        }
        return null;
    }
}
