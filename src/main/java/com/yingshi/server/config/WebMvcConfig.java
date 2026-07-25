package com.yingshi.server.config;

import com.yingshi.server.service.auth.CurrentUserArgumentResolver;
import com.yingshi.server.service.auth.JwtAuthenticationInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebMvcConfig.class);

    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    public WebMvcConfig(
            JwtAuthenticationInterceptor jwtAuthenticationInterceptor,
            CurrentUserArgumentResolver currentUserArgumentResolver
    ) {
        this.jwtAuthenticationInterceptor = jwtAuthenticationInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthenticationInterceptor);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // R3-SEC-003: Never default to wildcard origins with credentials.
        // If no origins configured, do not register any CORS mapping at all.
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            log.warn("CORS: app.cors.allowed-origins is empty. Cross-origin requests will be rejected. "
                    + "Set APP_CORS_ALLOWED_ORIGINS for your deployment.");
            return;
        }

        String[] origins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);
        if (origins.length == 0) {
            log.warn("CORS: configured origin list contains no usable origins.");
            return;
        }

        registry.addMapping("/api/**")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowedOrigins(origins)
                .allowCredentials(true)
                .maxAge(3600);
    }
}
