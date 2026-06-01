package com.yingshi.server.service.auth;

import java.time.Instant;

public record ParsedJwtToken(
        String userId,
        String account,
        String displayName,
        String libraryId,
        String sessionId,
        String tokenId,
        Instant issuedAt,
        Instant expireAt,
        JwtTokenType tokenType
) {
}
