package com.yingshi.server.service.auth;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.config.AuthLoginCodeProperties;
import com.yingshi.server.domain.AuthLoginChallengeEntity;
import com.yingshi.server.domain.UserEntity;
import com.yingshi.server.dto.auth.AuthLoginChallengeResponse;
import com.yingshi.server.repository.AuthLoginChallengeRepository;
import com.yingshi.server.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

@Service
public class AuthLoginChallengeService {

    private final AuthLoginChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthLoginCodeSender authLoginCodeSender;
    private final AuthLoginCodeProperties properties;
    private final AuthFailurePersistenceService authFailurePersistenceService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthLoginChallengeService(
            AuthLoginChallengeRepository challengeRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthLoginCodeSender authLoginCodeSender,
            AuthLoginCodeProperties properties,
            AuthFailurePersistenceService authFailurePersistenceService
    ) {
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authLoginCodeSender = authLoginCodeSender;
        this.properties = properties;
        this.authFailurePersistenceService = authFailurePersistenceService;
    }

    @Transactional
    public AuthLoginChallengeResponse issueChallenge(UserEntity user) {
        Instant now = Instant.now();
        enforceRateLimit(user.getAccount(), now);
        invalidateActiveChallenges(user.getAccount(), now);

        AuthLoginChallengeEntity challenge = new AuthLoginChallengeEntity();
        challenge.setId(IdGenerator.newId("login_challenge"));
        challenge.setUserId(user.getId());
        challenge.setAccount(user.getAccount());
        challenge.setExpireAt(now.plus(properties.ttl()));
        challenge.setResendAvailableAt(now.plus(properties.resendCooldown()));
        challenge.setLastSentAt(now);
        challenge.setSendCount(1);
        challenge.setFailedAttempts(0);
        challenge.setConsumedAt(null);
        challenge.setInvalidatedAt(null);

        String plainCode = generateCode();
        challenge.setCodeHash(passwordEncoder.encode(plainCode));
        challengeRepository.save(challenge);
        authLoginCodeSender.sendLoginCode(user.getAccount(), user.getDisplayName(), plainCode, challenge.getExpireAt());
        return toResponse(challenge);
    }

    @Transactional
    public AuthLoginChallengeResponse resendChallenge(String challengeId) {
        Instant now = Instant.now();
        AuthLoginChallengeEntity challenge = requireOpenChallenge(challengeId, now);
        if (challenge.getResendAvailableAt().isAfter(now)) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    ErrorCode.AUTH_LOGIN_CODE_RESEND_TOO_FAST,
                    "验证码发送过快，请稍后再试。"
            );
        }
        enforceRateLimit(challenge.getAccount(), now);
        UserEntity user = requireChallengeUser(challenge);
        String plainCode = generateCode();

        challenge.setCodeHash(passwordEncoder.encode(plainCode));
        challenge.setExpireAt(now.plus(properties.ttl()));
        challenge.setResendAvailableAt(now.plus(properties.resendCooldown()));
        challenge.setLastSentAt(now);
        challenge.setSendCount(challenge.getSendCount() + 1);
        challenge.setFailedAttempts(0);

        challengeRepository.save(challenge);
        authLoginCodeSender.sendLoginCode(user.getAccount(), user.getDisplayName(), plainCode, challenge.getExpireAt());
        return toResponse(challenge);
    }

    @Transactional
    public UserEntity verifyChallenge(String challengeId, String rawCode) {
        Instant now = Instant.now();
        AuthLoginChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> invalidChallenge("登录验证码会话不存在，请重新获取验证码。"));

        if (challenge.getConsumedAt() != null || challenge.getInvalidatedAt() != null) {
            throw invalidChallenge("登录验证码已失效，请重新获取验证码。");
        }
        if (challenge.getExpireAt().isBefore(now)) {
            // 过期失效：调用 AuthFailurePersistenceService（REQUIRES_NEW 独立事务），
            // 确保失效标记在外层事务回滚后仍持久化，防止过期验证码被重放。
            authFailurePersistenceService.recordChallengeFailure(challenge, null, now);
            throw new ApiException(
                    HttpStatus.GONE,
                    ErrorCode.AUTH_LOGIN_CODE_EXPIRED,
                    "验证码已过期，请重新获取验证码。"
            );
        }

        String normalizedCode = normalizeCode(rawCode);
        if (!passwordEncoder.matches(normalizedCode, challenge.getCodeHash())) {
            int nextFailedAttempts = challenge.getFailedAttempts() + 1;
            Instant invalidateAt = nextFailedAttempts >= properties.maxAttemptsPerChallenge() ? now : null;
            // 验证失败：调用 AuthFailurePersistenceService（REQUIRES_NEW 独立事务），
            // 确保失败计数/失效标记在外层事务回滚后仍持久化，防止暴力破解。
            authFailurePersistenceService.recordChallengeFailure(challenge, nextFailedAttempts, invalidateAt);
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.AUTH_LOGIN_CODE_INVALID,
                    "验证码错误，请重新输入。"
            );
        }

        challenge.setConsumedAt(now);
        challengeRepository.save(challenge);
        return requireChallengeUser(challenge);
    }

    private void enforceRateLimit(String account, Instant now) {
        long sentCount = challengeRepository.sumSendCountByAccountSince(
                account,
                now.minus(properties.rateLimitWindow())
        );
        if (sentCount >= properties.maxSendsPerWindow()) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    ErrorCode.AUTH_LOGIN_CODE_RATE_LIMITED,
                    "验证码发送次数已达上限，请稍后再试。"
            );
        }
    }

    private void invalidateActiveChallenges(String account, Instant now) {
        List<AuthLoginChallengeEntity> activeChallenges =
                challengeRepository.findByAccountAndConsumedAtIsNullAndInvalidatedAtIsNullAndExpireAtAfter(account, now);
        if (activeChallenges.isEmpty()) {
            return;
        }
        for (AuthLoginChallengeEntity challenge : activeChallenges) {
            challenge.setInvalidatedAt(now);
        }
        challengeRepository.saveAll(activeChallenges);
    }

    private AuthLoginChallengeEntity requireOpenChallenge(String challengeId, Instant now) {
        AuthLoginChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> invalidChallenge("登录验证码会话不存在，请重新获取验证码。"));
        if (challenge.getConsumedAt() != null || challenge.getInvalidatedAt() != null) {
            throw invalidChallenge("登录验证码已失效，请重新获取验证码。");
        }
        if (challenge.getExpireAt().isBefore(now)) {
            // 过期失效：调用 AuthFailurePersistenceService（REQUIRES_NEW 独立事务），
            // 确保失效标记在外层事务回滚后仍持久化，防止过期验证码被重放。
            authFailurePersistenceService.recordChallengeFailure(challenge, null, now);
            throw new ApiException(
                    HttpStatus.GONE,
                    ErrorCode.AUTH_LOGIN_CODE_EXPIRED,
                    "验证码已过期，请重新获取验证码。"
            );
        }
        return challenge;
    }

    private UserEntity requireChallengeUser(AuthLoginChallengeEntity challenge) {
        return userRepository.findById(challenge.getUserId())
                .orElseThrow(() -> invalidChallenge("登录验证码对应账号不存在，请重新获取验证码。"));
    }

    private AuthLoginChallengeResponse toResponse(AuthLoginChallengeEntity challenge) {
        return new AuthLoginChallengeResponse(
                challenge.getId(),
                maskEmail(challenge.getAccount()),
                challenge.getExpireAt().toEpochMilli(),
                challenge.getResendAvailableAt().toEpochMilli()
        );
    }

    private String generateCode() {
        int length = properties.length();
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index += 1) {
            builder.append(secureRandom.nextInt(10));
        }
        return builder.toString();
    }

    private String normalizeCode(String rawCode) {
        return rawCode == null ? "" : rawCode.trim();
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "***" + domain;
        }
        int prefixLength = Math.min(3, localPart.length() - 1);
        int suffixLength = Math.min(2, Math.max(1, localPart.length() - prefixLength));
        String prefix = localPart.substring(0, prefixLength);
        String suffix = localPart.substring(localPart.length() - suffixLength);
        return prefix + "***" + suffix + domain;
    }

    private ApiException invalidChallenge(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.AUTH_LOGIN_CHALLENGE_INVALID, message);
    }
}
