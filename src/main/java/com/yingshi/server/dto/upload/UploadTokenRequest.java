package com.yingshi.server.dto.upload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UploadTokenRequest(
        @NotBlank(message = "fileName is required.")
        @Size(max = 255, message = "fileName must be at most 255 characters.")
        String fileName,

        @NotBlank(message = "mimeType is required.")
        @Size(max = 120, message = "mimeType must be at most 120 characters.")
        String mimeType,

        @NotNull(message = "fileSizeBytes is required.")
        @Positive(message = "fileSizeBytes must be positive.")
        Long fileSizeBytes,

        @NotBlank(message = "mediaType is required.")
        String mediaType,

        @NotNull(message = "width is required.")
        @Positive(message = "width must be positive.")
        Integer width,

        @NotNull(message = "height is required.")
        @Positive(message = "height must be positive.")
        Integer height,

        Long durationMillis,

        @NotNull(message = "displayTimeMillis is required.")
        Long displayTimeMillis
) {
}
