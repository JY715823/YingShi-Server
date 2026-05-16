package com.yingshi.server.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthUpdateProfileRequest(
        @NotBlank
        @Size(max = 80)
        String displayName,

        @Size(max = 280)
        String bio
) {
}
