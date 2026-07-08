package com.yingshi.server.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthResendLoginChallengeRequest(
        @NotBlank(message = "challengeId is required.")
        @Size(max = 64, message = "challengeId must be at most 64 characters.") String challengeId
) {
}
