package com.yingshi.server;

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

/**
 * Seed data initializer for integration tests.
 * Creates two demo users and a shared library, mirroring the dev profile seed.
 */
@Configuration
@Profile("test")
public class TestSeedDataInitializer {

    public static final String DEMO_LIBRARY_ID = "library_shared";
    private static final String DEMO_PASSWORD = "123456";

    @Bean
    @Order(1)
    ApplicationRunner testAuthSeedRunner(
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
            library.setDisplayName("Test Shared Space");
            libraryRepository.save(library);

            UserEntity demoA = upsertUser(userRepository, passwordEncoder,
                    "user_demo_a", "1085060329@qq.com", "Demo A");
            UserEntity demoB = upsertUser(userRepository, passwordEncoder,
                    "user_demo_b", "2926315047@qq.com", "Demo B");

            upsertMember(libraryMemberRepository, "member_demo_a",
                    DEMO_LIBRARY_ID, demoA.getId(), SharedLibraryRole.OWNER);
            upsertMember(libraryMemberRepository, "member_demo_b",
                    DEMO_LIBRARY_ID, demoB.getId(), SharedLibraryRole.MEMBER);
        };
    }

    private UserEntity upsertUser(UserRepository repo, PasswordEncoder encoder,
                                  String id, String account, String displayName) {
        String normalized = account.trim().toLowerCase();
        UserEntity user = repo.findByAccount(normalized).orElseGet(() -> {
            UserEntity u = new UserEntity();
            u.setId(id);
            u.setAccount(normalized);
            return u;
        });
        user.setDisplayName(displayName);
        user.setBio("Test user");
        if (user.getPasswordHash() == null || !encoder.matches(DEMO_PASSWORD, user.getPasswordHash())) {
            user.setPasswordHash(encoder.encode(DEMO_PASSWORD));
        }
        user.setDefaultLibraryId(DEMO_LIBRARY_ID);
        return repo.save(user);
    }

    private void upsertMember(SharedLibraryMemberRepository repo, String id,
                              String libraryId, String userId, SharedLibraryRole role) {
        if (repo.existsByUserIdAndLibraryId(userId, libraryId)) return;
        SharedLibraryMemberEntity member = new SharedLibraryMemberEntity();
        member.setId(id);
        member.setLibraryId(libraryId);
        member.setUserId(userId);
        member.setRole(role);
        repo.save(member);
    }
}
