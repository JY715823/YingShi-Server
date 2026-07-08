package com.yingshi.server.config;

import com.yingshi.server.service.auth.CurrentUserArgumentResolver;
import com.yingshi.server.service.auth.JwtAuthenticationInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

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
        var mapping = registry.addMapping("/api/**")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            mapping.allowedOrigins(allowedOrigins.split(","));
        } else {
            mapping.allowedOriginPatterns("*");
        }
    }
}
