package com.yingshi.server;

import com.jayway.jsonpath.JsonPath;
import com.yingshi.server.domain.PushDeviceTokenEntity;
import com.yingshi.server.service.auth.AuthLoginCodeSender;
import com.yingshi.server.service.push.PushDeliveryResult;
import com.yingshi.server.service.push.PushMessageSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for PostgreSQL integration tests using Testcontainers.
 * <p>
 * Starts a real PostgreSQL container, applies Flyway migrations,
 * seeds test data via {@link TestSeedDataInitializer}, and provides
 * helper methods for login and media upload.
 * <p>
 * Subclasses inherit MockMvc, capturing beans, and auth helpers.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AbstractPostgresIntegrationTest.TestCapturingConfig.class)
public abstract class AbstractPostgresIntegrationTest {

    protected static final String ACCOUNT_A = "1085060329@qq.com";
    protected static final String ACCOUNT_B = "2926315047@qq.com";
    protected static final String TEMP_PASSWORD = "123456";
    protected static final String TEST_DEVICE_ID = "testcontainers-device";

    // Singleton container pattern: started once, shared across all test classes
    // to avoid stale datasource URLs when Spring caches the test context.
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("yingshi_test")
            .withUsername("test")
            .withPassword("test");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected CapturingPushMessageSender capturingPushSender;

    @Autowired
    protected CapturingAuthLoginCodeSender capturingCodeSender;

    // ---- Auth helpers ----

    protected String loginAndGetAccessToken() throws Exception {
        return loginAndGetAccessToken(ACCOUNT_A, TEMP_PASSWORD);
    }

    protected String loginAndGetAccessToken(String account, String password) throws Exception {
        return loginAndGetSession(account, password).accessToken();
    }

    protected AuthSessionTokens loginAndGetSession(String account, String password) throws Exception {
        MvcResult challengeResult = mockMvc.perform(post("/api/auth/login/challenge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account": "%s", "password": "%s"}
                                """.formatted(account, password)))
                .andExpect(status().isOk())
                .andReturn();
        String challengeId = readField(challengeResult, "data.challengeId");
        String code = capturingCodeSender.requireLatestCode(account);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId": "%s", "code": "%s", "deviceId": "%s"}
                                """.formatted(challengeId, code, TEST_DEVICE_ID)))
                .andExpect(status().isOk())
                .andReturn();
        return new AuthSessionTokens(
                readField(loginResult, "data.accessToken"),
                readField(loginResult, "data.refreshToken")
        );
    }

    // ---- Utility ----

    protected String readField(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$." + path);
    }

    protected byte[] jpegBytes() {
        try {
            BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpeg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---- Records & test config ----

    record AuthSessionTokens(String accessToken, String refreshToken) {}

    @TestConfiguration
    static class TestCapturingConfig {
        @Bean @Primary
        CapturingPushMessageSender capturingPushMessageSender() {
            return new CapturingPushMessageSender();
        }
        @Bean @Primary
        CapturingAuthLoginCodeSender capturingAuthLoginCodeSender() {
            return new CapturingAuthLoginCodeSender();
        }
    }

    static class CapturingPushMessageSender implements PushMessageSender {
        final List<CapturedDelivery> deliveries = new ArrayList<>();
        @Override
        public PushDeliveryResult sendDataMessage(List<PushDeviceTokenEntity> tokens, Map<String, String> data) {
            deliveries.add(new CapturedDelivery(tokens.stream().map(PushDeviceTokenEntity::getToken).toList(), Map.copyOf(data)));
            return new PushDeliveryResult(tokens.size(), tokens.size(), List.of());
        }
        void clear() { deliveries.clear(); }
    }

    record CapturedDelivery(List<String> tokens, Map<String, String> data) {}

    static class CapturingAuthLoginCodeSender implements AuthLoginCodeSender {
        final Map<String, String> codes = new ConcurrentHashMap<>();
        @Override
        public void sendLoginCode(String email, String displayName, String code, Instant expireAt) {
            codes.put(email, code);
        }
        String requireLatestCode(String email) {
            String code = codes.get(email);
            if (code == null) throw new IllegalStateException("No code captured for " + email);
            return code;
        }
    }
}
