package com.yingshi.server.dto.content;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MediaDto(
        String mediaId,
        String mediaType,
        String url,
        String previewUrl,
        String originalUrl,
        String videoUrl,
        String coverUrl,
        String mimeType,
        Long sizeBytes,
        Integer width,
        Integer height,
        Double aspectRatio,
        Long durationMillis,
        Long displayTimeMillis,
        Long capturedAtMillis,
        Long importedAtMillis,
        String displayTimeSource,
        String recordOwnerUserId,
        String uploadedByUserId,
        List<String> smallAlbumIds,
        List<MediaAccessDto> access,
        // FR-18: Location tracking (nullable, optional)
        Double latitude,
        Double longitude,
        String locationLabel,
        // V52: 位置来源 exif/inferred/manual
        String locationSource,
        // V49: EXIF拍摄参数(JSONB, 灵活扩展)
        java.util.Map<String, Object> exifMetadata
) {
    @JsonIgnore
    public List<String> postIds() {
        return smallAlbumIds;
    }
}
