package com.yingshi.server.dto.auth;

public record AuthCurrentUserResponse(
        String userId,
        String account,
        String displayName,
        String avatarUrl,
        String bio,
        String libraryId,
        String libraryDisplayName,
        AuthPartnerProfileResponse partner,
        long createdAtMillis,
        long updatedAtMillis
) {
}
