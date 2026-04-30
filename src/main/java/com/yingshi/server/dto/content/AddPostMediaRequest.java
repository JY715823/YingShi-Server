package com.yingshi.server.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddPostMediaRequest(
        @NotEmpty(message = "mediaIds is required.")
        List<@NotBlank(message = "mediaId is required.") String> mediaIds,
        String coverMediaId
) {
}
