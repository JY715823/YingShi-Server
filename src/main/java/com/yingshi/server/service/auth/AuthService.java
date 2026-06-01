package com.yingshi.server.service.auth;

import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.SharedLibraryEntity;
import com.yingshi.server.domain.SharedLibraryMemberEntity;
import com.yingshi.server.domain.UserEntity;
import com.yingshi.server.dto.auth.AuthCurrentUserResponse;
import com.yingshi.server.dto.auth.AuthLoginRequest;
import com.yingshi.server.dto.auth.AuthLoginResponse;
import com.yingshi.server.dto.auth.AuthLogoutRequest;
import com.yingshi.server.dto.auth.AuthLogoutResponse;
import com.yingshi.server.dto.auth.AuthPartnerProfileResponse;
import com.yingshi.server.dto.auth.AuthRefreshTokenRequest;
import com.yingshi.server.dto.auth.AuthRefreshTokenResponse;
import com.yingshi.server.dto.auth.AuthUpdateProfileRequest;
import com.yingshi.server.repository.SharedLibraryMemberRepository;
import com.yingshi.server.repository.SharedLibraryRepository;
import com.yingshi.server.repository.UserRepository;
import com.yingshi.server.service.upload.LocalMediaStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final SharedLibraryRepository libraryRepository;
    private final SharedLibraryMemberRepository libraryMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthSessionService authSessionService;
    private final LocalMediaStorageService localMediaStorageService;

    public AuthService(
            UserRepository userRepository,
            SharedLibraryRepository libraryRepository,
            SharedLibraryMemberRepository libraryMemberRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            AuthSessionService authSessionService,
            LocalMediaStorageService localMediaStorageService
    ) {
        this.userRepository = userRepository;
        this.libraryRepository = libraryRepository;
        this.libraryMemberRepository = libraryMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.authSessionService = authSessionService;
        this.localMediaStorageService = localMediaStorageService;
    }

    @Transactional
    public AuthLoginResponse login(AuthLoginRequest request) {
        UserEntity user = userRepository.findByAccount(request.account())
                .orElseThrow(() -> invalidCredentials());

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        SharedLibraryEntity library = getLibrary(user.getDefaultLibraryId());
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                user.getId(),
                user.getAccount(),
                user.getDisplayName(),
                library.getId(),
                null
        );
        JwtTokenBundle tokenBundle = jwtTokenService.issueTokensForNewSession(authenticatedUser);
        authSessionService.createSession(
                tokenBundle.sessionId(),
                user.getId(),
                library.getId(),
                tokenBundle.refreshTokenId(),
                tokenBundle.refreshExpireAt(),
                Instant.now()
        );

        return buildLoginResponse(user, library, tokenBundle);
    }

    public AuthCurrentUserResponse getCurrentUser(AuthenticatedUser currentUser) {
        UserEntity user = requireCurrentUser(currentUser.userId());
        SharedLibraryEntity library = getLibrary(currentUser.libraryId());

        return buildCurrentUserResponse(user, library);
    }

    @Transactional
    public AuthRefreshTokenResponse refreshToken(AuthRefreshTokenRequest request) {
        ParsedJwtToken refreshToken = jwtTokenService.parseRefreshToken(request.refreshToken().trim());
        UserEntity user = requireCurrentUser(refreshToken.userId());
        SharedLibraryEntity library = getLibrary(refreshToken.libraryId());
        var session = authSessionService.requireActiveRefreshSession(
                refreshToken.sessionId(),
                refreshToken.userId(),
                refreshToken.libraryId(),
                refreshToken.tokenId()
        );
        JwtTokenBundle tokenBundle = jwtTokenService.issueTokens(
                new AuthenticatedUser(
                        user.getId(),
                        user.getAccount(),
                        user.getDisplayName(),
                        library.getId(),
                        session.getId()
                ),
                session.getId()
        );
        authSessionService.rotateRefreshToken(
                session,
                tokenBundle.refreshTokenId(),
                tokenBundle.refreshExpireAt(),
                Instant.now()
        );
        return new AuthRefreshTokenResponse(
                tokenBundle.accessToken(),
                tokenBundle.refreshToken(),
                tokenBundle.accessTokenExpireAtMillis(),
                tokenBundle.refreshTokenExpireAtMillis()
        );
    }

    public AuthCurrentUserResponse updateCurrentUserProfile(
            AuthenticatedUser currentUser,
            AuthUpdateProfileRequest request
    ) {
        UserEntity user = requireCurrentUser(currentUser.userId());
        SharedLibraryEntity library = getLibrary(currentUser.libraryId());

        user.setDisplayName(request.displayName().trim());
        user.setBio(normalizeNullableText(request.bio()));
        userRepository.save(user);

        return buildCurrentUserResponse(user, library);
    }

    public AuthCurrentUserResponse uploadCurrentUserAvatar(
            AuthenticatedUser currentUser,
            MultipartFile file
    ) {
        UserEntity user = requireCurrentUser(currentUser.userId());
        SharedLibraryEntity library = getLibrary(currentUser.libraryId());
        user.setAvatarUrl(localMediaStorageService.storeAvatarImage(user.getId(), file));
        userRepository.save(user);
        return buildCurrentUserResponse(user, library);
    }

    public Resource loadAvatar(String userId, AuthenticatedUser currentUser) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.NOT_FOUND,
                        "Avatar owner was not found."
                ));
        if (!libraryMemberRepository.existsByUserIdAndLibraryId(user.getId(), currentUser.libraryId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN,
                    "Avatar is not accessible in the current shared library."
            );
        }
        String storagePath = normalizeNullableText(user.getAvatarUrl());
        if (storagePath == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Avatar has not been uploaded yet.");
        }
        return localMediaStorageService.load(storagePath);
    }

    @Transactional
    public AuthLogoutResponse logout(AuthenticatedUser currentUser, AuthLogoutRequest request) {
        String sessionId = currentUser.sessionId();
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            ParsedJwtToken refreshToken = jwtTokenService.parseRefreshToken(request.refreshToken().trim());
            if (!currentUser.userId().equals(refreshToken.userId())
                    || !currentUser.libraryId().equals(refreshToken.libraryId())) {
                throw new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.AUTH_SESSION_INVALID,
                        "Refresh token does not belong to the current user session."
                );
            }
            sessionId = refreshToken.sessionId();
        }
        authSessionService.revokeSession(sessionId, currentUser.userId(), currentUser.libraryId());
        return new AuthLogoutResponse(true);
    }

    private SharedLibraryEntity getLibrary(String libraryId) {
        return libraryRepository.findById(libraryId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.AUTH_SESSION_INVALID,
                        "Current shared library does not exist."
                ));
    }

    private AuthLoginResponse buildLoginResponse(
            UserEntity user,
            SharedLibraryEntity library,
            JwtTokenBundle tokenBundle
    ) {
        AuthPartnerProfileResponse partner = findPartnerProfile(user, library.getId());
        return new AuthLoginResponse(
                user.getId(),
                user.getAccount(),
                user.getDisplayName(),
                resolveAvatarUrl(user),
                normalizeNullableText(user.getBio()),
                library.getId(),
                library.getDisplayName(),
                partner,
                user.getCreatedAt().toEpochMilli(),
                user.getUpdatedAt().toEpochMilli(),
                tokenBundle.accessToken(),
                tokenBundle.refreshToken(),
                tokenBundle.accessTokenExpireAtMillis(),
                tokenBundle.refreshTokenExpireAtMillis()
        );
    }

    private AuthCurrentUserResponse buildCurrentUserResponse(UserEntity user, SharedLibraryEntity library) {
        AuthPartnerProfileResponse partner = findPartnerProfile(user, library.getId());
        return new AuthCurrentUserResponse(
                user.getId(),
                user.getAccount(),
                user.getDisplayName(),
                resolveAvatarUrl(user),
                normalizeNullableText(user.getBio()),
                library.getId(),
                library.getDisplayName(),
                partner,
                user.getCreatedAt().toEpochMilli(),
                user.getUpdatedAt().toEpochMilli()
        );
    }

    private AuthPartnerProfileResponse findPartnerProfile(UserEntity currentUser, String libraryId) {
        List<SharedLibraryMemberEntity> members = libraryMemberRepository.findByLibraryId(libraryId);
        List<String> memberUserIds = members.stream()
                .map(SharedLibraryMemberEntity::getUserId)
                .distinct()
                .toList();
        if (memberUserIds.isEmpty()) {
            return null;
        }

        Map<String, UserEntity> usersById = userRepository.findByIdIn(memberUserIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        Optional<UserEntity> partner = memberUserIds.stream()
                .filter(memberUserId -> !memberUserId.equals(currentUser.getId()))
                .map(usersById::get)
                .filter(user -> user != null)
                .sorted(Comparator.comparing(UserEntity::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .findFirst();

        return partner.map(this::toPartnerProfile).orElse(null);
    }

    private AuthPartnerProfileResponse toPartnerProfile(UserEntity user) {
        return new AuthPartnerProfileResponse(
                user.getId(),
                user.getAccount(),
                user.getDisplayName(),
                resolveAvatarUrl(user),
                normalizeNullableText(user.getBio())
        );
    }

    private UserEntity requireCurrentUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.AUTH_UNAUTHORIZED,
                        "Current user does not exist."
                ));
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveAvatarUrl(UserEntity user) {
        return normalizeNullableText(user.getAvatarUrl()) == null ? null : "/api/auth/avatar/" + user.getId();
    }

    private ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.AUTH_INVALID_CREDENTIALS,
                "Invalid account or password."
        );
    }
}
