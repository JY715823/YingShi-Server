package com.yingshi.server.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAlbumRequest(
        @NotBlank(message = "title is required.")
        @Size(max = 120, message = "title must be at most 120 characters.")
        String title,

        @Size(max = 255, message = "subtitle must be at most 255 characters.")
        String subtitle
) {
}
