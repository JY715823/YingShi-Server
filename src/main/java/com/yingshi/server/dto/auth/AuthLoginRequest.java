package com.yingshi.server.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthLoginRequest(
        @NotBlank(message = "account is required.")
        @Size(max = 120, message = "account must be at most 120 characters.") String account,
        @NotBlank(message = "password is required.")
        @Size(max = 256, message = "password must be at most 256 characters.") String password
) {
}
