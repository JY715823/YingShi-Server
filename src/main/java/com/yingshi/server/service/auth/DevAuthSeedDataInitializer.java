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
@Profile("dev")
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
            if (userRepository.count() > 0 || libraryRepository.count() > 0 || libraryMemberRepository.count() > 0) {
                return;
            }

            SharedLibraryEntity library = new SharedLibraryEntity();
            library.setId(DEMO_LIBRARY_ID);
            library.setDisplayName("YingShi Shared Library");
            libraryRepository.save(library);

            UserEntity demoA = createUser("user_demo_a", "demo.a@yingshi.local", "Demo A", passwordEncoder);
            UserEntity demoB = createUser("user_demo_b", "demo.b@yingshi.local", "Demo B", passwordEncoder);
            userRepository.save(demoA);
            userRepository.save(demoB);

            libraryMemberRepository.save(createMember("member_demo_a", DEMO_LIBRARY_ID, demoA.getId(), SharedLibraryRole.OWNER));
            libraryMemberRepository.save(createMember("member_demo_b", DEMO_LIBRARY_ID, demoB.getId(), SharedLibraryRole.MEMBER));
        };
    }

    private UserEntity createUser(String id, String account, String displayName, PasswordEncoder passwordEncoder) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setAccount(account);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        user.setDefaultLibraryId(DEMO_LIBRARY_ID);
        return user;
    }

    private SharedLibraryMemberEntity createMember(String id, String libraryId, String userId, SharedLibraryRole role) {
        SharedLibraryMemberEntity member = new SharedLibraryMemberEntity();
        member.setId(id);
        member.setLibraryId(libraryId);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }
}
