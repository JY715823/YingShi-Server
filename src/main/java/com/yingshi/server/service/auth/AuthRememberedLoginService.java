package com.yingshi.server.service.auth;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.config.AuthRememberedLoginProperties;
import com.yingshi.server.domain.AuthRememberedLoginEntity;
import com.yingshi.server.domain.UserEntity;
import com.yingshi.server.repository.AuthRememberedLoginRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class AuthRememberedLoginService {

    private final AuthRememberedLoginRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuthRememberedLoginProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthRememberedLoginService(
            AuthRememberedLoginRepository repository,
            PasswordEncoder passwordEncoder,
            AuthRememberedLoginProperties properties
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
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
            entity.setRevokedAt(now);
            repository.save(entity);
            throw expiredRememberedLogin("当前设备的免验证码登录已过期，请重新获取验证码。");
        }
        if (!passwordEncoder.matches(normalizeToken(rawToken), entity.getTokenHash())) {
            entity.setRevokedAt(now);
            repository.save(entity);
            throw invalidRememberedLogin("当前设备的免验证码登录已失效，请重新获取验证码。");
        }

        String refreshedToken = generateToken();
        entity.setAccount(user.getAccount());
        entity.setTokenHash(passwordEncoder.encode(refreshedToken));
        entity.setExpireAt(now.plus(properties.ttl()));
        entity.setLastAuthenticatedAt(now);
        entity.setLastUsedAt(now);
        entity.setRevokedAt(null);
        repository.save(entity);
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
