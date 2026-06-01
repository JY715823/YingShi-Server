package com.yingshi.server.service.auth;

import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.AuthSessionEntity;
import com.yingshi.server.repository.AuthSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthSessionService {

    private final AuthSessionRepository authSessionRepository;

    public AuthSessionService(AuthSessionRepository authSessionRepository) {
        this.authSessionRepository = authSessionRepository;
    }

    @Transactional
    public AuthSessionEntity createSession(
            String sessionId,
            String userId,
            String libraryId,
            String refreshTokenId,
            Instant refreshExpireAt,
            Instant authenticatedAt
    ) {
        AuthSessionEntity session = new AuthSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setLibraryId(libraryId);
        session.setRefreshTokenId(refreshTokenId);
        session.setRefreshExpireAt(refreshExpireAt);
        session.setLastAuthenticatedAt(authenticatedAt);
        session.setRevokedAt(null);
        return authSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public AuthSessionEntity requireActiveAccessSession(String sessionId, String userId, String libraryId) {
        AuthSessionEntity session = requireSession(sessionId, userId, libraryId);
        if (session.getRevokedAt() != null) {
            throw invalidSession("Current session has been logged out.");
        }
        return session;
    }

    @Transactional
    public AuthSessionEntity requireActiveRefreshSession(
            String sessionId,
            String userId,
            String libraryId,
            String refreshTokenId
    ) {
        AuthSessionEntity session = requireSession(sessionId, userId, libraryId);
        if (session.getRevokedAt() != null) {
            throw invalidSession("Refresh session has already been revoked.");
        }
        Instant now = Instant.now();
        if (session.getRefreshExpireAt().isBefore(now)) {
            session.setRevokedAt(now);
            authSessionRepository.save(session);
            throw invalidSession("Refresh session has expired.");
        }
        if (!session.getRefreshTokenId().equals(refreshTokenId)) {
            session.setRevokedAt(now);
            authSessionRepository.save(session);
            throw invalidSession("Refresh token has already been rotated.");
        }
        return session;
    }

    @Transactional
    public void rotateRefreshToken(
            AuthSessionEntity session,
            String newRefreshTokenId,
            Instant newRefreshExpireAt,
            Instant authenticatedAt
    ) {
        session.setRefreshTokenId(newRefreshTokenId);
        session.setRefreshExpireAt(newRefreshExpireAt);
        session.setLastAuthenticatedAt(authenticatedAt);
        authSessionRepository.save(session);
    }

    @Transactional
    public void revokeSession(String sessionId, String userId, String libraryId) {
        AuthSessionEntity session = requireSession(sessionId, userId, libraryId);
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(Instant.now());
            authSessionRepository.save(session);
        }
    }

    private AuthSessionEntity requireSession(String sessionId, String userId, String libraryId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw invalidSession("Session id is missing.");
        }
        return authSessionRepository.findByIdAndUserIdAndLibraryId(sessionId, userId, libraryId)
                .orElseThrow(() -> invalidSession("Current session was not found."));
    }

    private ApiException invalidSession(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_SESSION_INVALID, message);
    }
}
