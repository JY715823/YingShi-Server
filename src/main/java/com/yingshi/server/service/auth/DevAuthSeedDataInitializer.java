package com.yingshi.server.service.auth;

import com.yingshi.server.domain.SharedLibraryEntity;
import com.yingshi.server.domain.SharedLibraryMemberEntity;
import com.yingshi.server.domain.SharedLibraryRole;
import com.yingshi.server.domain.UserEntity;
import com.yingshi.server.repository.SharedLibraryMemberRepository;
import com.yingshi.server.repository.SharedLibraryRepository;
import com.yingshi.server.repository.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile({"dev", "docker", "docker-local"})
public class DevAuthSeedDataInitializer {

    public static final String DEMO_LIBRARY_ID = "library_shared";
    private static final String DEMO_PASSWORD = "demo123456";

    @Bean
    @Order(1)
    ApplicationRunner authSeedRunner(
            UserRepository userRepository,
            SharedLibraryRepository libraryRepository,
            SharedLibraryMemberRepository libraryMemberRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            SharedLibraryEntity library = libraryRepository.findById(DEMO_LIBRARY_ID)
                    .orElseGet(() -> {
                        SharedLibraryEntity created = new SharedLibraryEntity();
                        created.setId(DEMO_LIBRARY_ID);
                        return created;
                    });
            library.setDisplayName("\u6211\u4eec\u7684\u5c0f\u7a7a\u95f4");
            libraryRepository.save(library);

            UserEntity demoA = upsertUser(
                    userRepository,
                    passwordEncoder,
                    "user_demo_a",
                    "demo.a@yingshi.local",
                    "\u6620\u4e16\u5c0f\u5c4b",
                    "\u4e00\u8d77\u628a\u5e73\u5e38\u65e5\u5b50\u6162\u6162\u6536\u8fdb\u8fd9\u5ea7\u5c0f\u5c0f\u76f8\u518c\u3002"
            );
            UserEntity demoB = upsertUser(
                    userRepository,
                    passwordEncoder,
                    "user_demo_b",
                    "demo.b@yingshi.local",
                    "\u53e6\u4e00\u534a",
                    "\u628a\u751f\u6d3b\u91cc\u7684\u95ea\u5149\u7247\u6bb5\uff0c\u4e5f\u628a\u5b89\u9759\u548c\u60f3\u5ff5\u4e00\u8d77\u7559\u4e0b\u6765\u3002"
            );

            upsertMember(libraryMemberRepository, "member_demo_a", DEMO_LIBRARY_ID, demoA.getId(), SharedLibraryRole.OWNER);
            upsertMember(libraryMemberRepository, "member_demo_b", DEMO_LIBRARY_ID, demoB.getId(), SharedLibraryRole.MEMBER);
        };
    }

    private UserEntity upsertUser(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            String id,
            String account,
            String displayName,
            String bio
    ) {
        UserEntity user = userRepository.findByAccount(account)
                .orElseGet(() -> {
                    UserEntity created = new UserEntity();
                    created.setId(id);
                    created.setAccount(account);
                    return created;
        });
        user.setAccount(account);
        user.setDisplayName(displayName);
        user.setBio(bio);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(DEMO_PASSWORD, user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        }
        user.setDefaultLibraryId(DEMO_LIBRARY_ID);
        return userRepository.save(user);
    }

    private void upsertMember(
            SharedLibraryMemberRepository libraryMemberRepository,
            String id,
            String libraryId,
            String userId,
            SharedLibraryRole role
    ) {
        if (libraryMemberRepository.existsByUserIdAndLibraryId(userId, libraryId)) {
            return;
        }
        SharedLibraryMemberEntity member = new SharedLibraryMemberEntity();
        member.setId(id);
        member.setLibraryId(libraryId);
        member.setUserId(userId);
        member.setRole(role);
        libraryMemberRepository.save(member);
    }
}
