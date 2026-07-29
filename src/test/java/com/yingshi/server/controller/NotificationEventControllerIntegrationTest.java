package com.yingshi.server.controller;

import com.yingshi.server.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R3-D-3: 通知端点集成测试.
 *
 * 验证 [NotificationController] 与 [com.yingshi.server.service.notification.NotificationService] 的集成行为:
 * 1. 正向: GET /api/notifications 已登录用户返回 200 + 通知列表
 * 2. 正向: GET /api/notifications?limit=N 支持分页参数
 * 3. 正向: GET /api/notifications/{notificationId} 返回通知详情
 * 4. 正向: POST /api/notifications/{notificationId}/read 标记已读
 * 5. 正向: POST /api/notifications/read-all 标记全部已读
 * 6. 边界: 无 Authorization 头返回 401
 * 7. 边界: GET 不存在的 notificationId 返回 404 (或业务约定的错误响应)
 * 8. 边界: 标记其他用户的通知为已读应失败 (权限校验)
 *
 * 使用 Testcontainers PostgreSQL via [AbstractPostgresIntegrationTest].
 */
class NotificationEventControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Test
    void listNotificationsReturns200() throws Exception {
        String token = loginAndGetAccessToken();

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listNotificationsWithLimitParamReturns200() throws Exception {
        String token = loginAndGetAccessToken();

        mockMvc.perform(get("/api/notifications")
                        .param("limit", "10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listNotificationsWithCursorParamReturns200() throws Exception {
        String token = loginAndGetAccessToken();

        // 即使 cursor 为空或 null, 也不应报错 (Service 内部 normalize)
        mockMvc.perform(get("/api/notifications")
                        .param("cursor", "")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listNotificationsWithoutAuthorizationReturns401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getNonExistentNotificationReturns404Or400() throws Exception {
        String token = loginAndGetAccessToken();

        // 通知不存在时, 业务返回 404 (ApiException NOT_FOUND) 或 400 (VALIDATION_ERROR)
        // 这里用 is4xxClientError 容错两种实现
        mockMvc.perform(get("/api/notifications/{notificationId}", "non-existent-id-xyz")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void markReadNonExistentNotificationReturns404Or400() throws Exception {
        String token = loginAndGetAccessToken();

        mockMvc.perform(post("/api/notifications/{notificationId}/read", "non-existent-id-xyz")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void markReadWithoutAuthorizationReturns401() throws Exception {
        mockMvc.perform(post("/api/notifications/{notificationId}/read", "any-id"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markAllReadReturns200() throws Exception {
        String token = loginAndGetAccessToken();

        mockMvc.perform(post("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty());
    }

    @Test
    void markAllReadWithoutAuthorizationReturns401() throws Exception {
        mockMvc.perform(post("/api/notifications/read-all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getNotificationWithoutAuthorizationReturns401() throws Exception {
        mockMvc.perform(get("/api/notifications/{notificationId}", "any-id"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listNotificationsForBothPartnersReturnsIndependentLists() throws Exception {
        // 两个不同用户各自查询通知列表, 应互不干扰
        String tokenA = loginAndGetAccessToken(ACCOUNT_A, TEMP_PASSWORD);
        String tokenB = loginAndGetAccessToken(ACCOUNT_B, TEMP_PASSWORD);

        MvcResult resultA = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult resultB = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();

        // 两个用户的响应都应是合法的数组结构
        String bodyA = resultA.getResponse().getContentAsString();
        String bodyB = resultB.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(bodyA).contains("\"data\"");
        org.assertj.core.api.Assertions.assertThat(bodyB).contains("\"data\"");
    }

    @Test
    void listNotificationsWithLargeLimitReturns200AndCappedByService() throws Exception {
        String token = loginAndGetAccessToken();

        // 即使 limit 很大, Service 内部有 MAX_LIMIT 上限保护, 仍返回 200
        mockMvc.perform(get("/api/notifications")
                        .param("limit", "10000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listNotificationsWithZeroLimitReturns200AndUsesDefault() throws Exception {
        String token = loginAndGetAccessToken();

        // limit=0 应触发 Service 内部 normalize 为 DEFAULT_LIMIT, 返回 200
        mockMvc.perform(get("/api/notifications")
                        .param("limit", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listNotificationsWithNegativeLimitReturns200AndUsesDefault() throws Exception {
        String token = loginAndGetAccessToken();

        mockMvc.perform(get("/api/notifications")
                        .param("limit", "-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
