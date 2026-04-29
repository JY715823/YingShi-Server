package com.yingshi.server.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreatePostRequest(
        @NotBlank(message = "title is required.")
        @Size(max = 120, message = "title must be at most 120 characters.")
        String title,

        @Size(max = 1000, message = "summary must be at most 1000 characters.")
        String summary,

        @Size(max = 120, message = "contributorLabel must be at most 120 characters.")
        String contributorLabel,

        @NotNull(message = "displayTimeMillis is required.")
        Long displayTimeMillis,

        @NotEmpty(message = "albumIds is required.")
        List<@NotBlank(message = "albumId is required.") String> albumIds,

        @NotEmpty(message = "initialMediaIds is required.")
        List<@NotBlank(message = "mediaId is required.") String> initialMediaIds,

        String coverMediaId
) {
}
