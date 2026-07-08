package com.yingshi.server.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRememberedLoginRequest(
        @NotBlank(message = "account is required.")
        @Size(max = 120, message = "account must be at most 120 characters.") String account,
        @NotBlank(message = "password is required.")
        @Size(max = 256, message = "password must be at most 256 characters.") String password,
        @NotBlank(message = "deviceId is required.")
        @Size(max = 128, message = "deviceId must be at most 128 characters.") String deviceId,
        @NotBlank(message = "rememberedLoginToken is required.")
        @Size(max = 512, message = "rememberedLoginToken must be at most 512 characters.") String rememberedLoginToken
) {
}
