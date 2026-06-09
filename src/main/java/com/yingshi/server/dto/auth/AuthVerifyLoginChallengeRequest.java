package com.yingshi.server.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record AuthVerifyLoginChallengeRequest(
        @NotBlank(message = "challengeId is required.") String challengeId,
        @NotBlank(message = "code is required.") String code,
        @NotBlank(message = "deviceId is required.") String deviceId
) {
}
