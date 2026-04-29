package com.yingshi.server.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record AuthLoginRequest(
        @NotBlank(message = "account is required.") String account,
        @NotBlank(message = "password is required.") String password
) {
}
