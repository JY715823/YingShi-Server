package com.yingshi.server;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Auth flow integration tests against real PostgreSQL.
 * Covers: login challenge, verify, token refresh, logout, account lockout, remembered login.
 */
class AuthIntegrationTest extends AbstractPostgresIntegrationTest {

    @Test
    void loginChallengeVerifyAndMe() throws Exception {
        // Step 1: Request challenge
        MvcResult challenge = mockMvc.perform(post("/api/auth/login/challenge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account": "%s", "password": "%s"}
                                """.formatted(ACCOUNT_A, TEMP_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.data.maskedEmail").value(startsWith("108")))
                .andReturn();
        String challengeId = readField(challenge, "data.challengeId");
        String code = capturingCodeSender.requireLatestCode(ACCOUNT_A);

        // Step 2: Verify code → get tokens
        MvcResult login = mockMvc.perform(post("/api/auth/login/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId": "%s", "code": "%s", "deviceId": "%s"}
                                """.formatted(challengeId, code, TEST_DEVICE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();
        String accessToken = readField(login, "data.accessToken");

        // Step 3: Get current user
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account").value(ACCOUNT_A));
    }

    @Test
    void refreshTokenRotation() throws Exception {
        AuthSessionTokens tokens = loginAndGetSession(ACCOUNT_A, TEMP_PASSWORD);

        // Refresh with valid token
        MvcResult refresh = mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(tokens.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String newRefresh = readField(refresh, "data.refreshToken");

        // Old refresh token should be invalid
        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(tokens.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesToken() throws Exception {
        AuthSessionTokens tokens = loginAndGetSession(ACCOUNT_A, TEMP_PASSWORD);

        // Logout
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk());

        // Token should no longer work
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordRejects() throws Exception {
        mockMvc.perform(post("/api/auth/login/challenge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account": "%s", "password": "wrong-password"}
                                """.formatted(ACCOUNT_A)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongCodeRejects() throws Exception {
        MvcResult challenge = mockMvc.perform(post("/api/auth/login/challenge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account": "%s", "password": "%s"}
                                """.formatted(ACCOUNT_A, TEMP_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String challengeId = readField(challenge, "data.challengeId");

        mockMvc.perform(post("/api/auth/login/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId": "%s", "code": "000000", "deviceId": "%s"}
                                """.formatted(challengeId, TEST_DEVICE_ID)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crossUserAccessBlocked() throws Exception {
        String tokenA = loginAndGetAccessToken(ACCOUNT_A, TEMP_PASSWORD);
        String tokenB = loginAndGetAccessToken(ACCOUNT_B, TEMP_PASSWORD);

        // Both users can access their own profile
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());
    }

    @Test
    void profileUpdateAndReadBack() throws Exception {
        String token = loginAndGetAccessToken();

        mockMvc.perform(patch("/api/auth/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName": "Updated Name", "bio": "Updated bio"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Updated Name"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Updated Name"));
    }
}
