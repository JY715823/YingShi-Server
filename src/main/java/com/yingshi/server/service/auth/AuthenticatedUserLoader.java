package com.yingshi.server.service.auth;

import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.UserEntity;
import com.yingshi.server.repository.SharedLibraryMemberRepository;
import com.yingshi.server.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthenticatedUserLoader {

    private final UserRepository userRepository;
    private final SharedLibraryMemberRepository libraryMemberRepository;

    public AuthenticatedUserLoader(
            UserRepository userRepository,
            SharedLibraryMemberRepository libraryMemberRepository
    ) {
        this.userRepository = userRepository;
        this.libraryMemberRepository = libraryMemberRepository;
    }

    public AuthenticatedUser loadCurrentUser(String userId, String libraryId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.AUTH_UNAUTHORIZED,
                        "Current user does not exist."
                ));

        if (!libraryMemberRepository.existsByUserIdAndLibraryId(userId, libraryId)) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.AUTH_SESSION_INVALID,
                    "Current user is not a member of the shared library."
            );
        }

        return new AuthenticatedUser(user.getId(), user.getAccount(), user.getDisplayName(), libraryId);
    }
}
