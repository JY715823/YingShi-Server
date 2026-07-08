package com.yingshi.server.dto.auth;

import jakarta.validation.constraints.Size;

public record AuthLogoutRequest(
        @Size(max = 2048, message = "refreshToken must be at most 2048 characters.") String refreshToken
) {
}
