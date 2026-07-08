package com.yingshi.server.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthVerifyLoginChallengeRequest(
        @NotBlank(message = "challengeId is required.")
        @Size(max = 64, message = "challengeId must be at most 64 characters.") String challengeId,
        @NotBlank(message = "code is required.")
        @Size(max = 32, message = "code must be at most 32 characters.") String code,
        @NotBlank(message = "deviceId is required.")
        @Size(max = 128, message = "deviceId must be at most 128 characters.") String deviceId
) {
}
