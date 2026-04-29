package com.yingshi.server.dto.content;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MediaDto(
        String mediaId,
        String mediaType,
        String previewUrl,
        String originalUrl,
        String videoUrl,
        String coverUrl,
        Integer width,
        Integer height,
        Double aspectRatio,
        Long displayTimeMillis,
        List<String> postIds
) {
}
