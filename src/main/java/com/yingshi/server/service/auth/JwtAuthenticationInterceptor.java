package com.yingshi.server.service.auth;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtAuthenticationInterceptor implements HandlerInterceptor {

    public static final String CURRENT_USER_ATTRIBUTE = "currentUser";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final AuthenticatedUserLoader authenticatedUserLoader;

    public JwtAuthenticationInterceptor(
            JwtTokenService jwtTokenService,
            AuthenticatedUserLoader authenticatedUserLoader
    ) {
        this.jwtTokenService = jwtTokenService;
        this.authenticatedUserLoader = authenticatedUserLoader;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod) || !isAuthRequired(handlerMethod)) {
            return true;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.AUTH_UNAUTHORIZED,
                    "Bearer token is required."
            );
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.AUTH_UNAUTHORIZED,
                    "Bearer token is required."
            );
        }

        AuthenticatedUser tokenUser = jwtTokenService.parseAccessToken(token);
        AuthenticatedUser currentUser = authenticatedUserLoader.loadCurrentUser(tokenUser.userId(), tokenUser.spaceId());
        request.setAttribute(CURRENT_USER_ATTRIBUTE, currentUser);
        return true;
    }

    private boolean isAuthRequired(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(AuthRequired.class)
                || handlerMethod.getBeanType().isAnnotationPresent(AuthRequired.class);
    }
}
