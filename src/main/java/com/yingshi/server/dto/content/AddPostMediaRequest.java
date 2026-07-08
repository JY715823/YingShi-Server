package com.yingshi.server.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AddPostMediaRequest(
        @NotEmpty(message = "mediaIds is required.")
        List<@NotBlank(message = "mediaId is required.") String> mediaIds,
        @Size(max = 64, message = "coverMediaId must be at most 64 characters.")
        String coverMediaId
) {
}
