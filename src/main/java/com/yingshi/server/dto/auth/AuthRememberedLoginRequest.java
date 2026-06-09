package com.yingshi.server.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record AuthRememberedLoginRequest(
        @NotBlank(message = "account is required.") String account,
        @NotBlank(message = "password is required.") String password,
        @NotBlank(message = "deviceId is required.") String deviceId,
        @NotBlank(message = "rememberedLoginToken is required.") String rememberedLoginToken
) {
}
