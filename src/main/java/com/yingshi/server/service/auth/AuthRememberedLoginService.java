package com.yingshi.server.service.auth;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.config.AuthRememberedLoginProperties;
import com.yingshi.server.domain.AuthRememberedLoginEntity;
import com.yingshi.server.domain.UserEntity;
import com.yingshi.server.repository.AuthRememberedLoginRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class AuthRememberedLoginService {

    private static final Logger log = LoggerFactory.getLogger(AuthRememberedLoginService.class);

    private final AuthRememberedLoginRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuthRememberedLoginProperties properties;
    private final AuthFailurePersistenceService authFailurePersistenceService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthRememberedLoginService(
            AuthRememberedLoginRepository repository,
            PasswordEncoder passwordEncoder,
            AuthRememberedLoginProperties properties,
            AuthFailurePersistenceService authFailurePersistenceService
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.authFailurePersistenceService = authFailurePersistenceService;
    }

    @Transactional
    public AuthRememberedLoginGrant issueGrant(UserEntity user, String deviceId) {
        Instant now = Instant.now();
        AuthRememberedLoginEntity entity = repository.findByUserIdAndDeviceId(
                        user.getId(),
                        normalizeDeviceId(deviceId)
                )
                .orElseGet(AuthRememberedLoginEntity::new);
        if (entity.getId() == null || entity.getId().isBlank()) {
            entity.setId(IdGenerator.newId("remembered_login"));
        }
        String token = generateToken();
        entity.setUserId(user.getId());
        entity.setAccount(user.getAccount());
        entity.setDeviceId(normalizeDeviceId(deviceId));
        entity.setTokenHash(passwordEncoder.encode(token));
        entity.setExpireAt(now.plus(properties.ttl()));
        entity.setLastAuthenticatedAt(now);
        entity.setLastUsedAt(now);
        entity.setRevokedAt(null);
        repository.save(entity);
        return new AuthRememberedLoginGrant(token, entity.getExpireAt().toEpochMilli());
    }

    /**
     * 校验并轮换 remembered login token。
     *
     * <p>H4 修复关键点: rotation 操作由 @Version 乐观锁保护（CAS 单次消费）。
     * 并发场景下若两个 authenticate 请求同时通过校验并尝试 rotation,
     * 第一个 save 成功 (version+1), 第二个 save 触发 OptimisticLockingFailureException。
     * 此处捕获异常并返回 409 让客户端重新登录, 而非让 OptimisticLockException 上抛被
     * GlobalExceptionHandler 包装为 500。
     *
     * <p>注意: 与 refresh token 不同, remembered login 并发冲突不需要撤销整个会话族,
     * 因为 remembered login 只在单设备单点使用, 不存在重放攻击面。
     */
    @Transactional
    public AuthRememberedLoginGrant authenticate(UserEntity user, String deviceId, String rawToken) {
        Instant now = Instant.now();
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        AuthRememberedLoginEntity entity = repository.findByUserIdAndDeviceId(user.getId(), normalizedDeviceId)
                .orElseThrow(() -> invalidRememberedLogin("当前设备的免验证码登录已失效，请重新获取验证码。"));

        if (entity.getRevokedAt() != null) {
            throw invalidRememberedLogin("当前设备的免验证码登录已失效，请重新获取验证码。");
        }
        if (entity.getExpireAt().isBefore(now)) {
            // 过期撤销：调用 AuthFailurePersistenceService（REQUIRES_NEW 独立事务），
            // 确保撤销状态在外层事务回滚后仍持久化，防止过期凭证被重放。
            authFailurePersistenceService.revokeRememberedLogin(entity);
            throw expiredRememberedLogin("当前设备的免验证码登录已过期，请重新获取验证码。");
        }
        if (!passwordEncoder.matches(normalizeToken(rawToken), entity.getTokenHash())) {
            // 令牌不匹配撤销：调用 AuthFailurePersistenceService（REQUIRES_NEW 独立事务），
            // 确保撤销状态在外层事务回滚后仍持久化，防止不匹配凭证被重放。
            authFailurePersistenceService.revokeRememberedLogin(entity);
            throw invalidRememberedLogin("当前设备的免验证码登录已失效，请重新获取验证码。");
        }

        // rotation 操作由 @Version 乐观锁保护（CAS 单次消费）
        String refreshedToken = generateToken();
        entity.setAccount(user.getAccount());
        entity.setTokenHash(passwordEncoder.encode(refreshedToken));
        entity.setExpireAt(now.plus(properties.ttl()));
        entity.setLastAuthenticatedAt(now);
        entity.setLastUsedAt(now);
        entity.setRevokedAt(null);
        try {
            repository.save(entity);
        } catch (OptimisticLockingFailureException ex) {
            // CAS 失败: 并发 remembered login rotation 冲突
            log.warn("Concurrent remembered login rotation detected for userId={} deviceId={}, returning 409",
                    user.getId(), normalizedDeviceId);
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.AUTH_REMEMBERED_LOGIN_INVALID,
                    "并发登录冲突，请重新获取验证码。"
            );
        }
        return new AuthRememberedLoginGrant(refreshedToken, entity.getExpireAt().toEpochMilli());
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeDeviceId(String deviceId) {
        String normalized = deviceId == null ? "" : deviceId.trim();
        if (normalized.isEmpty()) {
            throw invalidRememberedLogin("设备标识缺失，请重新获取验证码。");
        }
        return normalized;
    }

    private String normalizeToken(String token) {
        return token == null ? "" : token.trim();
    }

    private ApiException invalidRememberedLogin(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REMEMBERED_LOGIN_INVALID, message);
    }

    private ApiException expiredRememberedLogin(String message) {
        return new ApiException(HttpStatus.GONE, ErrorCode.AUTH_REMEMBERED_LOGIN_EXPIRED, message);
    }
}
