package com.yingshi.server.dto.content;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
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

        Long eventStartedAtMillis,

        Long eventEndedAtMillis,

        @Size(max = 20, message = "displayTimeSource must be at most 20 characters.")
        String displayTimeSource,

        @NotBlank(message = "albumId is required.")
        String albumId,

        @NotNull(message = "initialMediaIds is required.")
        List<@NotBlank(message = "mediaId is required.") String> initialMediaIds,

        String coverMediaId
) {
    @JsonIgnore
    public List<String> albumIds() {
        return List.of(albumId);
    }
}
