package com.yingshi.server.service.auth;

import com.yingshi.server.domain.SpaceEntity;
import com.yingshi.server.domain.SpaceMemberEntity;
import com.yingshi.server.domain.SpaceRole;
import com.yingshi.server.domain.UserEntity;
import com.yingshi.server.repository.SpaceMemberRepository;
import com.yingshi.server.repository.SpaceRepository;
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

    public static final String DEMO_SPACE_ID = "space_demo_shared";
    private static final String DEMO_PASSWORD = "demo123456";

    @Bean
    @Order(1)
    ApplicationRunner authSeedRunner(
            UserRepository userRepository,
            SpaceRepository spaceRepository,
            SpaceMemberRepository spaceMemberRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (userRepository.count() > 0 || spaceRepository.count() > 0 || spaceMemberRepository.count() > 0) {
                return;
            }

            SpaceEntity space = new SpaceEntity();
            space.setId(DEMO_SPACE_ID);
            space.setDisplayName("Yingshi Demo Space");
            spaceRepository.save(space);

            UserEntity demoA = createUser("user_demo_a", "demo.a@yingshi.local", "Demo A", passwordEncoder);
            UserEntity demoB = createUser("user_demo_b", "demo.b@yingshi.local", "Demo B", passwordEncoder);
            userRepository.save(demoA);
            userRepository.save(demoB);

            spaceMemberRepository.save(createMember("member_demo_a", DEMO_SPACE_ID, demoA.getId(), SpaceRole.OWNER));
            spaceMemberRepository.save(createMember("member_demo_b", DEMO_SPACE_ID, demoB.getId(), SpaceRole.MEMBER));
        };
    }

    private UserEntity createUser(String id, String account, String displayName, PasswordEncoder passwordEncoder) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setAccount(account);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        user.setDefaultSpaceId(DEMO_SPACE_ID);
        return user;
    }

    private SpaceMemberEntity createMember(String id, String spaceId, String userId, SpaceRole role) {
        SpaceMemberEntity member = new SpaceMemberEntity();
        member.setId(id);
        member.setSpaceId(spaceId);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }
}
