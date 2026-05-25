package com.yingshi.server.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record AuthRefreshTokenRequest(
        @NotBlank
        String refreshToken
) {
}
