package com.yingshi.server.dto.auth;

public record AuthLoginChallengeResponse(
        String challengeId,
        String maskedEmail,
        long expireAtMillis,
        long resendAvailableAtMillis
) {
}
