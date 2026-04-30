package com.yingshi.server.dto.content;

import com.fasterxml.jackson.annotation.JsonInclude;

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
        List<String> postIds
) {
}
