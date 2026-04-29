package com.yingshi.server.dto.content;

import jakarta.validation.constraints.NotBlank;

public record UpdatePostCoverRequest(
        @NotBlank(message = "coverMediaId is required.") String coverMediaId
) {
}
