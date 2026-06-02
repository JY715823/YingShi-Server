package com.yingshi.server.dto.life;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record LifeConsoleMediaRequest(
        @NotBlank(message = "category is required.")
        String category,

        @NotEmpty(message = "mediaIds is required.")
        List<@NotBlank(message = "mediaId is required.") String> mediaIds
) {
}
