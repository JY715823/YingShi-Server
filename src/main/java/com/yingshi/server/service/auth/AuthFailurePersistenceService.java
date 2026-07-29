package com.yingshi.server.service.auth;

import com.yingshi.server.config.AuthAccountLockoutProperties;
import com.yingshi.server.domain.AuthLoginChallengeEntity;
import com.yingshi.server.domain.AuthRememberedLoginEntity;
import com.yingshi.server.domain.AuthSessionEntity;
import com.yingshi.server.domain.UserEntity;
import com.yingshi.server.repository.AuthLoginChallengeRepository;
import com.yingshi.server.repository.AuthRememberedLoginRepository;
import com.yingshi.server.repository.AuthSessionRepository;
import com.yingshi.server.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 集中管理认证失败/撤销状态的独立事务持久化。
 *
 * <p>这些操作必须在 {@link Propagation#REQUIRES_NEW} 独立事务中执行，
 * 确保外层业务事务回滚后状态仍能持久化，防止凭证被重放、暴力破解计数丢失等安全问题。
 *
 * <p>本 bean 必须由外部 bean 调用（不可被同类自调用），
 * 否则 Spring AOP 代理不会生效，{@code REQUIRES_NEW} 注解将被忽略。
 * 历史上 AuthService / AuthSessionService / AuthLoginChallengeService / AuthRememberedLoginService
 * 曾将此类方法标注为 {@code REQUIRES_NEW} 但在同类内自调用，导致事务隔离完全失效。
 *
 * <p>关键安全场景：
 * <ul>
 *   <li>refresh token 重放检测：撤销整个会话族必须在外层事务回滚后仍生效</li>
 *   <li>登录密码错误：失败计数累加必须在抛出 ApiException 后仍持久化（触发账号锁定）</li>
 *   <li>登录验证码错误/过期：失败计数或失效标记必须持久化</li>
 *   <li>remembered login 过期/不匹配：撤销状态必须持久化</li>
 * </ul>
 */
@Service
public class AuthFailurePersistenceService {

    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final AuthLoginChallengeRepository authLoginChallengeRepository;
    private final AuthRememberedLoginRepository authRememberedLoginRepository;
    private final AuthAccountLockoutProperties accountLockoutProperties;

    public AuthFailurePersistenceService(
            UserRepository userRepository,
            AuthSessionRepository authSessionRepository,
            AuthLoginChallengeRepository authLoginChallengeRepository,
            AuthRememberedLoginRepository authRememberedLoginRepository,
            AuthAccountLockoutProperties accountLockoutProperties
    ) {
        this.userRepository = userRepository;
        this.authSessionRepository = authSessionRepository;
        this.authLoginChallengeRepository = authLoginChallengeRepository;
        this.authRememberedLoginRepository = authRememberedLoginRepository;
        this.accountLockoutProperties = accountLockoutProperties;
    }

    /**
     * 在独立事务中记录登录失败并累加失败计数。
     *
     * <p>重读最新用户状态避免脏读，确保失败计数在独立事务中递增。
     * 当失败次数达到阈值时设置账号锁定时间。
     *
     * <p>必须为 REQUIRES_NEW：调用方在记录失败后会抛出 ApiException 触发外层事务回滚，
     * 若不独立事务则失败计数会随外层回滚而丢失，导致账号锁定机制失效、可无限暴力破解。
     *
     * @param user 登录失败的用户实体（仅用于获取 id，内部会重读最新状态）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLogin(UserEntity user) {
        if (!accountLockoutProperties.isEnabled()) {
            return;
        }
        // 重读最新状态避免脏读，确保失败计数在独立事务中递增
        UserEntity fresh = userRepository.findById(user.getId()).orElseThrow();
        int attempts = fresh.getFailedLoginAttempts() + 1;
        fresh.setFailedLoginAttempts(attempts);
        if (attempts >= accountLockoutProperties.getMaxAttempts()) {
            fresh.setLockedUntil(Instant.now().plus(accountLockoutProperties.getLockDuration()));
        }
        userRepository.save(fresh);
    }

    /**
     * 在独立事务中撤销单个会话（用于 refresh token 过期场景）。
     *
     * <p>必须为 REQUIRES_NEW：调用方在撤销后会抛出 ApiException 触发外层事务回滚，
     * 若不独立事务则撤销状态会随外层回滚而丢失，过期会话仍可被重放使用。
     *
     * @param session 待撤销的会话实体
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeSession(AuthSessionEntity session) {
        session.setRevokedAt(Instant.now());
        authSessionRepository.save(session);
    }

    /**
     * 在独立事务中撤销整个会话族（用于 refresh token 重放检测）。
     *
     * <p>必须为 REQUIRES_NEW：检测到 refresh token 重放时，必须撤销该用户在该库下的所有会话，
     * 调用方随后会抛出 ApiException 触发外层事务回滚。
     * 若不独立事务则会话族撤销会随外层回滚而丢失，重放攻击者仍可使用旧 token。
     *
     * @param userId    用户 id
     * @param libraryId 共享库 id
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeSessionFamily(String userId, String libraryId) {
        authSessionRepository.revokeAllByUserAndLibrary(userId, libraryId, Instant.now());
    }

    /**
     * 在独立事务中持久化登录验证码失败状态。
     *
     * <p>必须为 REQUIRES_NEW：调用方在记录失败后会抛出 ApiException 触发外层事务回滚，
     * 若不独立事务则失败计数/失效标记会随外层回滚而丢失，验证码可被无限重试暴力破解。
     *
     * @param challenge           待更新的验证码实体（方法内会 set 字段并 save）
     * @param nextFailedAttempts  新的失败次数（null 表示不更新失败次数，仅失效）
     * @param invalidateAt        失效时间戳（null 表示不失效，仅更新失败次数）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordChallengeFailure(
            AuthLoginChallengeEntity challenge,
            Integer nextFailedAttempts,
            Instant invalidateAt
    ) {
        if (nextFailedAttempts != null) {
            challenge.setFailedAttempts(nextFailedAttempts);
        }
        if (invalidateAt != null) {
            challenge.setInvalidatedAt(invalidateAt);
        }
        authLoginChallengeRepository.save(challenge);
    }

    /**
     * 在独立事务中撤销 remembered login 凭证（用于过期或令牌不匹配场景）。
     *
     * <p>必须为 REQUIRES_NEW：调用方在撤销后会抛出 ApiException 触发外层事务回滚，
     * 若不独立事务则撤销状态会随外层回滚而丢失，过期/不匹配的 remembered login 仍可被重放。
     *
     * @param login 待撤销的 remembered login 实体
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeRememberedLogin(AuthRememberedLoginEntity login) {
        login.setRevokedAt(Instant.now());
        authRememberedLoginRepository.save(login);
    }
}
