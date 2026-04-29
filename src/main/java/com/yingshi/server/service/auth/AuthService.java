package com.yingshi.server.service.auth;

import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.SpaceEntity;
import com.yingshi.server.domain.UserEntity;
import com.yingshi.server.dto.auth.AuthCurrentUserResponse;
import com.yingshi.server.dto.auth.AuthLoginRequest;
import com.yingshi.server.dto.auth.AuthLoginResponse;
import com.yingshi.server.dto.auth.AuthLogoutResponse;
import com.yingshi.server.repository.SpaceRepository;
import com.yingshi.server.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final SpaceRepository spaceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            UserRepository userRepository,
            SpaceRepository spaceRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userRepository = userRepository;
        this.spaceRepository = spaceRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public AuthLoginResponse login(AuthLoginRequest request) {
        UserEntity user = userRepository.findByAccount(request.account())
                .orElseThrow(() -> invalidCredentials());

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        SpaceEntity space = getSpace(user.getDefaultSpaceId());
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                user.getId(),
                user.getAccount(),
                user.getDisplayName(),
                space.getId()
        );
        JwtTokenBundle tokenBundle = jwtTokenService.issueTokens(authenticatedUser);

        return new AuthLoginResponse(
                user.getId(),
                user.getAccount(),
                user.getDisplayName(),
                space.getId(),
                space.getDisplayName(),
                tokenBundle.accessToken(),
                tokenBundle.refreshToken(),
                tokenBundle.accessTokenExpireAtMillis(),
                tokenBundle.refreshTokenExpireAtMillis()
        );
    }

    public AuthCurrentUserResponse getCurrentUser(AuthenticatedUser currentUser) {
        UserEntity user = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.AUTH_UNAUTHORIZED,
                        "Current user does not exist."
                ));
        SpaceEntity space = getSpace(currentUser.spaceId());

        return new AuthCurrentUserResponse(
                user.getId(),
                user.getAccount(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                space.getId(),
                space.getDisplayName()
        );
    }

    public AuthLogoutResponse logout() {
        return new AuthLogoutResponse(true);
    }

    private SpaceEntity getSpace(String spaceId) {
        return spaceRepository.findById(spaceId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.AUTH_SESSION_INVALID,
                        "Current space does not exist."
                ));
    }

    private ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.AUTH_INVALID_CREDENTIALS,
                "Invalid account or password."
        );
    }
}
