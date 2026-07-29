package com.yingshi.server.service.auth;

import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.AuthSessionEntity;
import com.yingshi.server.repository.AuthSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthSessionService {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionService.class);

    private final AuthSessionRepository authSessionRepository;
    private final AuthFailurePersistenceService authFailurePersistenceService;

    public AuthSessionService(
            AuthSessionRepository authSessionRepository,
            AuthFailurePersistenceService authFailurePersistenceService
    ) {
        this.authSessionRepository = authSessionRepository;
        this.authFailurePersistenceService = authFailurePersistenceService;
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

    /**
     * 校验 refresh token 关联的 session 是否有效（未撤销/未过期/refreshTokenId 匹配）。
     *
     * <p>C2 修复关键点: 不再使用 {@code findByIdAndUserIdAndLibraryIdWithLock} (PESSIMISTIC_WRITE)。
     * 原因: 外层事务持有行锁后调用 {@code authFailurePersistenceService.revokeSession} (REQUIRES_NEW)
     * 会形成死锁——REQUIRES_NEW 挂起外层事务但锁仍持有, 内层事务等待同一行的锁释放。
     *
     * <p>并发安全保证: 由 {@link #rotateRefreshToken} 的 @Version CAS 兜底,
     * 并发 refresh 仅一个能成功 commit, 其余触发 OptimisticLockException 由该方法处理。
     *
     * @param sessionId      JWT refresh token 中声明的 session id
     * @param userId         用户 id
     * @param libraryId      共享库 id
     * @param refreshTokenId JWT refresh token 中声明的 token id (用于重放检测)
     */
    @Transactional
    public AuthSessionEntity requireActiveRefreshSession(
            String sessionId,
            String userId,
            String libraryId,
            String refreshTokenId
    ) {
        if (sessionId == null || sessionId.isBlank()) {
            throw invalidSession("Session id is missing.");
        }
        AuthSessionEntity session = authSessionRepository
                .findByIdAndUserIdAndLibraryId(sessionId, userId, libraryId)
                .orElseThrow(() -> invalidSession("Current session was not found."));
        if (session.getRevokedAt() != null) {
            throw invalidSession("Refresh session has already been revoked.");
        }
        Instant now = Instant.now();
        if (session.getRefreshExpireAt().isBefore(now)) {
            // 过期撤销：调用 AuthFailurePersistenceService（REQUIRES_NEW 独立事务），
            // 确保撤销状态在外层事务回滚后仍持久化，防止过期会话被重放。
            // C2 修复后外层事务不再持有行锁，REQUIRES_NEW 可正常获取行锁完成撤销。
            authFailurePersistenceService.revokeSession(session);
            throw invalidSession("Refresh session has expired.");
        }
        if (!session.getRefreshTokenId().equals(refreshTokenId)) {
            // 重放检测：撤销整个会话族（REQUIRES_NEW 独立事务），
            // 确保会话族撤销在外层事务回滚后仍持久化，防止重放攻击者继续使用旧 token。
            // revokeAllByUserAndLibrary 是 @Modifying UPDATE（绕过 @Version CAS），无锁冲突。
            authFailurePersistenceService.revokeSessionFamily(userId, libraryId);
            throw invalidSession("Refresh token has already been rotated.");
        }
        return session;
    }

    /**
     * 轮换 refresh token: 写入新 tokenId/expireAt + 触发 @Version CAS 乐观锁。
     *
     * <p>H4 修复关键点: 并发场景下若两个 refresh 请求同时通过校验并尝试 rotation,
     * 第一个 save 成功 (version+1), 第二个 save 触发 OptimisticLockingFailureException。
     * 此处捕获异常并撤销整个会话族 (REQUIRES_NEW), 防止攻击者继续使用旧 token,
     * 同时返回 409 让客户端重新登录, 而非让 OptimisticLockException 上抛被 GlobalExceptionHandler
     * 包装为 500。
     *
     * @param session             已通过 requireActiveRefreshSession 校验的 session 实体
     * @param newRefreshTokenId   新 refresh token id
     * @param newRefreshExpireAt   新 refresh token 过期时间
     * @param authenticatedAt      认证时间
     */
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
        try {
            authSessionRepository.save(session);
        } catch (OptimisticLockingFailureException ex) {
            // CAS 失败: 并发 refresh rotation 冲突, 撤销整个会话族防止重放
            log.warn("Concurrent refresh rotation detected for session userId={} libraryId={}, revoking session family",
                    session.getUserId(), session.getLibraryId());
            authFailurePersistenceService.revokeSessionFamily(session.getUserId(), session.getLibraryId());
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.AUTH_SESSION_INVALID,
                    "Concurrent refresh token rotation. Please log in again."
            );
        }
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
