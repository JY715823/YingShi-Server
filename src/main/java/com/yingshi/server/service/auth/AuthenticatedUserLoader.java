package com.yingshi.server.service.auth;

import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.UserEntity;
import com.yingshi.server.repository.SpaceMemberRepository;
import com.yingshi.server.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthenticatedUserLoader {

    private final UserRepository userRepository;
    private final SpaceMemberRepository spaceMemberRepository;

    public AuthenticatedUserLoader(
            UserRepository userRepository,
            SpaceMemberRepository spaceMemberRepository
    ) {
        this.userRepository = userRepository;
        this.spaceMemberRepository = spaceMemberRepository;
    }

    public AuthenticatedUser loadCurrentUser(String userId, String spaceId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.AUTH_UNAUTHORIZED,
                        "Current user does not exist."
                ));

        if (!spaceMemberRepository.existsByUserIdAndSpaceId(userId, spaceId)) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.AUTH_SESSION_INVALID,
                    "Current user is not a member of the requested space."
            );
        }

        return new AuthenticatedUser(user.getId(), user.getAccount(), user.getDisplayName(), spaceId);
    }
}
