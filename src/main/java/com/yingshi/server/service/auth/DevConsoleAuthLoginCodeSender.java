package com.yingshi.server.service.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Dev 环境验证码发送器：将验证码输出到日志，不依赖 SMTP 配置。
 * 仅在 SmtpAuthLoginCodeSender 未注册（SMTP 配置不完整）时启用。
 * SMTP 配置完整时由 AuthConfig 注册 SmtpAuthLoginCodeSender 真正发送邮件，
 * 本类因 @ConditionalOnMissingBean 不生效。
 *
 * 注意：AuthConfig 中 SmtpAuthLoginCodeSender 的 @ConditionalOnMissingBean 必须移除，
 * 否则两个 sender 都未注册时会形成循环依赖。
 * 此处用 @ConditionalOnMissingBean(AuthLoginCodeSender.class) 保证只在没有
 * 其他 AuthLoginCodeSender 实现时启用（即 SMTP sender 未注册时）。
 */
@Component
@Profile({"dev", "docker-local"})
@ConditionalOnMissingBean(AuthLoginCodeSender.class)
public class DevConsoleAuthLoginCodeSender implements AuthLoginCodeSender {

    private static final Logger log = LoggerFactory.getLogger(DevConsoleAuthLoginCodeSender.class);
    private static final DateTimeFormatter EXPIRE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Shanghai"));

    @Override
    public void sendLoginCode(String email, String displayName, String code, Instant expireAt) {
        String name = (displayName == null || displayName.isBlank()) ? "你" : displayName.trim();
        log.info("""

                ====================================================
                [DEV 登录验证码]
                用户: {} ({})
                验证码: {}
                失效时间: {}（北京时间）
                ====================================================
                """, name, email, code, EXPIRE_TIME_FORMATTER.format(expireAt));
    }
}
