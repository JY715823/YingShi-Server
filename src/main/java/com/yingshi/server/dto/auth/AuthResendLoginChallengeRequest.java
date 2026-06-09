package com.yingshi.server.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record AuthResendLoginChallengeRequest(
        @NotBlank(message = "challengeId is required.") String challengeId
) {
}
