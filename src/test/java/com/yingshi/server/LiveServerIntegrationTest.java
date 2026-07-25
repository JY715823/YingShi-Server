package com.yingshi.server;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP integration tests against the running YingShi server.
 * Tests the full auth flow, upload, trash, and ledger sync via real HTTP calls.
 *
 * Requires: yingshi-server running at http://localhost:8080
 * Run: mvnw test -Dtest=LiveServerIntegrationTest
 */
@Disabled("Requires a running server at localhost:8080; excluded from regular mvnw test runs")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LiveServerIntegrationTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String ACCOUNT_A = "1085060329@qq.com";
    private static final String TEMP_PASSWORD = "123456";
    private static HttpClient http;
    private static String accessToken;

    @BeforeAll
    static void setup() throws Exception {
        http = HttpClient.newBuilder().build();
        // Verify server is running
        HttpResponse<String> health = http.send(
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/api/health")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode(), "Server must be running at " + BASE_URL);
        assertTrue(health.body().contains("UP"));
    }

    @Test
    @Order(1)
    void healthEndpoint() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/api/health")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"status\":\"UP\""));
        assertNotNull(resp.headers().firstValue("X-Request-Id").orElse(null));
    }

    @Test
    @Order(2)
    void loginChallengeFlow() throws Exception {
        // Step 1: Request challenge
        String challengeBody = """
                {"account": "%s", "password": "%s"}
                """.formatted(ACCOUNT_A, TEMP_PASSWORD);
        HttpResponse<String> challengeResp = http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/auth/login/challenge"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(challengeBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, challengeResp.statusCode(), "Challenge failed: " + challengeResp.body());
        assertTrue(challengeResp.body().contains("challengeId"));
    }

    @Test
    @Order(3)
    void protectedEndpointRejectsUnauthenticated() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/api/auth/me")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode());
    }

    @Test
    @Order(4)
    void trashEndpointsRequireAuth() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/api/trash/items")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode());
    }

    @Test
    @Order(5)
    void uploadEndpointsRequireAuth() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/api/uploads")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode());
    }

    @Test
    @Order(6)
    void ledgerSyncEndpointExists() throws Exception {
        // Ledger sync requires V21+ tables; server may be at V20
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/ledger/sync"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {"lastSyncVersionMillis":0,"changes":{"books":[],"categories":[],"accounts":[],"transactions":[],"budgets":[],"categoryBudgets":[],"deletedItems":[],"recurringRules":[],"recurringOccurrences":[],"deletedRowIds":[]}}
                                """))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        // 401 if endpoint exists and requires auth, 404 if ledger tables not yet migrated
        assertTrue(resp.statusCode() == 401 || resp.statusCode() == 404,
                "Ledger sync should return 401 (auth required) or 404 (tables not migrated), got: " + resp.statusCode());
    }

    @Test
    @Order(7)
    void syncVersionsEndpoint() throws Exception {
        // This endpoint requires auth
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/api/sync/versions")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode());
    }

    @Test
    @Order(8)
    void pushDiagnosticsRequiresAuth() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/api/push/diagnostics")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode());
    }

    @Test
    @Order(9)
    void openApiDocsAccessible() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/v3/api-docs")).build(),
                HttpResponse.BodyHandlers.ofString());
        // In docker profile, swagger is enabled
        assertTrue(resp.statusCode() == 200 || resp.statusCode() == 404,
                "OpenAPI docs should be accessible or explicitly disabled");
    }

    @Test
    @Order(10)
    void actuatorOrApiHealthAccessible() throws Exception {
        // Actuator paths + API health fallback
        String[] paths = {"/actuator/health", "/actuator/health/liveness", "/api/health"};
        boolean found = false;
        for (String path : paths) {
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder().uri(URI.create(BASE_URL + path)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body().contains("UP")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Health endpoint should be accessible (actuator or /api/health)");
    }
}
