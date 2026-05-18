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
import com.yingshi.server.dto.auth.AuthLogoutResponse;
import com.yingshi.server.dto.auth.AuthPartnerProfileResponse;
import com.yingshi.server.dto.auth.AuthUpdateProfileRequest;
import com.yingshi.server.repository.SharedLibraryMemberRepository;
import com.yingshi.server.repository.SharedLibraryRepository;
import com.yingshi.server.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    public AuthService(
            UserRepository userRepository,
            SharedLibraryRepository libraryRepository,
            SharedLibraryMemberRepository libraryMemberRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userRepository = userRepository;
        this.libraryRepository = libraryRepository;
        this.libraryMemberRepository = libraryMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

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
                library.getId()
        );
        JwtTokenBundle tokenBundle = jwtTokenService.issueTokens(authenticatedUser);

        return buildLoginResponse(user, library, tokenBundle);
    }

    public AuthCurrentUserResponse getCurrentUser(AuthenticatedUser currentUser) {
        UserEntity user = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.AUTH_UNAUTHORIZED,
                "Current user does not exist."
                ));
        SharedLibraryEntity library = getLibrary(currentUser.libraryId());

        return buildCurrentUserResponse(user, library);
    }

    public AuthCurrentUserResponse updateCurrentUserProfile(
            AuthenticatedUser currentUser,
            AuthUpdateProfileRequest request
    ) {
        UserEntity user = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.AUTH_UNAUTHORIZED,
                        "Current user does not exist."
                ));
        SharedLibraryEntity library = getLibrary(currentUser.libraryId());

        user.setDisplayName(request.displayName().trim());
        user.setBio(normalizeNullableText(request.bio()));
        userRepository.save(user);

        return buildCurrentUserResponse(user, library);
    }

    public AuthLogoutResponse logout() {
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
                normalizeNullableText(user.getAvatarUrl()),
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
                normalizeNullableText(user.getAvatarUrl()),
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
                normalizeNullableText(user.getAvatarUrl()),
                normalizeNullableText(user.getBio())
        );
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.AUTH_INVALID_CREDENTIALS,
                "Invalid account or password."
        );
    }
}
