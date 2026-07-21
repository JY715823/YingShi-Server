package com.yingshi.server.config;

import com.yingshi.server.service.auth.AuthLoginCodeSender;
import com.yingshi.server.service.auth.SmtpAuthLoginCodeSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
    @ConditionalOnMissingBean(AuthLoginCodeSender.class)
    public AuthLoginCodeSender authLoginCodeSender(AuthMailProperties authMailProperties) {
        return new SmtpAuthLoginCodeSender(authMailProperties);
    }
}
