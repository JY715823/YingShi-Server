package com.yingshi.server.config;

import com.yingshi.server.service.auth.AuthLoginCodeSender;
import com.yingshi.server.service.auth.SmtpAuthLoginCodeSender;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties({
        AuthProperties.class,
        AuthLoginCodeProperties.class,
        AuthRememberedLoginProperties.class,
        AuthMailProperties.class,
        AuthRateLimitProperties.class,
        AuthAccountLockoutProperties.class,
        StorageProperties.class,
        FcmProperties.class,
        GeocodingProperties.class
})
public class AuthConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthLoginCodeSender authLoginCodeSender(AuthMailProperties authMailProperties) {
        // SMTP sender 总是注册. DevConsoleAuthLoginCodeSender 用
        // @ConditionalOnMissingBean(AuthLoginCodeSender.class) 在本 bean 注册后自动失效.
        // dev 环境若 SMTP 配置完整会真发邮件; 否则 DevConsoleAuthLoginCodeSender 兜底走日志.
        return new SmtpAuthLoginCodeSender(authMailProperties);
    }
}
