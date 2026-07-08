package com.yingshi.server.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePostCoverRequest(
        @NotBlank(message = "coverMediaId is required.")
        @Size(max = 64, message = "coverMediaId must be at most 64 characters.")
        String coverMediaId
) {
}
