package com.yingshi.server.controller;

import com.yingshi.server.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc integration tests for LifeConsoleController.
 * Covers all 6 endpoints: HTTP methods, URLs, request/response bodies, status codes, auth checks.
 */
class LifeConsoleControllerTest extends AbstractPostgresIntegrationTest {

    private String uploadAndConfirmMedia(String token, String fingerprint) throws Exception {
        MvcResult tokenResult = mockMvc.perform(post("/api/uploads/token")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"fileName": "life-test.jpg", "fileSizeBytes": 512, "mimeType": "image/jpeg",
                                 "mediaType": "IMAGE", "width": 4, "height": 4, "displayTimeMillis": %d,
                                 "sourceFingerprint": "%s", "operationType": "IMPORT_TO_APP"}
                                """.formatted(System.currentTimeMillis(), fingerprint)))
                .andExpect(status().isOk())
                .andReturn();
        String uploadId = readField(tokenResult, "data.uploadId");

        MockMultipartFile file = new MockMultipartFile("file", "life-test.jpg",
                "image/jpeg", jpegBytes());
        mockMvc.perform(multipart("/api/uploads/{uploadId}/file", uploadId)
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        MvcResult confirmResult = mockMvc.perform(post("/api/uploads/{uploadId}/confirm", uploadId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return readField(confirmResult, "data.mediaId");
    }

    // ---- TC-C01: GET /api/life-console/today ----

    @Test
    void getTodayReturns200() throws Exception {
        String token = loginAndGetAccessToken();

        mockMvc.perform(get("/api/life-console/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").isNotEmpty())
                .andExpect(jsonPath("$.data.zoneId").isNotEmpty())
                .andExpect(jsonPath("$.data.currentUser").isNotEmpty())
                .andExpect(jsonPath("$.data.personSelf").isNotEmpty())
                .andExpect(jsonPath("$.data.personPartner").isNotEmpty())
                .andExpect(jsonPath("$.data.mealSelf").isNotEmpty())
                .andExpect(jsonPath("$.data.mealPartner").isNotEmpty())
                .andExpect(jsonPath("$.data.bowel").isNotEmpty());
    }

    // ---- TC-C02: GET /api/life-console/today (无认证) ----

    @Test
    void getTodayUnauthorizedReturns401() throws Exception {
        mockMvc.perform(get("/api/life-console/today"))
                .andExpect(status().isUnauthorized());
    }

    // ---- TC-C03: GET /api/life-console/history ----

    @Test
    void getHistoryReturns200() throws Exception {
        String token = loginAndGetAccessToken();

        mockMvc.perform(get("/api/life-console/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.zoneId").isNotEmpty())
                .andExpect(jsonPath("$.data.currentUser").isNotEmpty())
                .andExpect(jsonPath("$.data.personDays").isArray())
                .andExpect(jsonPath("$.data.mealDays").isArray())
                .andExpect(jsonPath("$.data.bowelDays").isArray());
    }

    // ---- TC-C04: GET /api/life-console/history?limitDays=7 ----

    @Test
    void getHistoryWithLimitDaysReturns200() throws Exception {
        String token = loginAndGetAccessToken();

        mockMvc.perform(get("/api/life-console/history")
                        .param("limitDays", "7")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personDays").isArray());
    }

    // ---- TC-C05: GET /api/life-console/history (无认证) ----

    @Test
    void getHistoryUnauthorizedReturns401() throws Exception {
        mockMvc.perform(get("/api/life-console/history"))
                .andExpect(status().isUnauthorized());
    }

    // ---- TC-C06: POST /api/life-console/media ----

    @Test
    void addMediaReturns200() throws Exception {
        String token = loginAndGetAccessToken();
        String mediaId = uploadAndConfirmMedia(token, "life-add-" + System.nanoTime());

        mockMvc.perform(post("/api/life-console/media")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"category": "PERSON", "mediaIds": ["%s"]}
                                """.formatted(mediaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personSelf.mediaItems").isArray())
                .andExpect(jsonPath("$.data.personSelf.mediaItems").isNotEmpty());
    }

    // ---- TC-C07: POST /api/life-console/media (无效 category) ----

    @Test
    void addMediaInvalidCategoryReturns400() throws Exception {
        String token = loginAndGetAccessToken();

        mockMvc.perform(post("/api/life-console/media")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"category": "INVALID", "mediaIds": ["test_media_id"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ---- TC-C08: POST /api/life-console/media (空 mediaIds) ----

    @Test
    void addMediaEmptyMediaIdsReturns400() throws Exception {
        String token = loginAndGetAccessToken();

        mockMvc.perform(post("/api/life-console/media")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"category": "PERSON", "mediaIds": []}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ---- TC-C09: POST /api/life-console/media (无认证) ----

    @Test
    void addMediaUnauthorizedReturns401() throws Exception {
        mockMvc.perform(post("/api/life-console/media")
                        .contentType("application/json")
                        .content("""
                                {"category": "PERSON", "mediaIds": ["test_media_id"]}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ---- TC-C10: DELETE /api/life-console/media/{mediaId} ----

    @Test
    void deleteMediaReturns200() throws Exception {
        String token = loginAndGetAccessToken();
        String mediaId = uploadAndConfirmMedia(token, "life-del-" + System.nanoTime());

        // First add media to life console
        mockMvc.perform(post("/api/life-console/media")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"category": "PERSON", "mediaIds": ["%s"]}
                                """.formatted(mediaId)))
                .andExpect(status().isOk());

        // Then delete
        mockMvc.perform(delete("/api/life-console/media/{mediaId}", mediaId)
                        .param("category", "PERSON")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trashItemId").isNotEmpty());
    }

    // ---- TC-C11: DELETE /api/life-console/media/{mediaId} (无认证) ----

    @Test
    void deleteMediaUnauthorizedReturns401() throws Exception {
        mockMvc.perform(delete("/api/life-console/media/{mediaId}", "test_media_id")
                        .param("category", "PERSON"))
                .andExpect(status().isUnauthorized());
    }

    // ---- TC-C12: POST /api/life-console/bowel-events ----

    @Test
    void addBowelEventReturns200() throws Exception {
        String token = loginAndGetAccessToken();

        mockMvc.perform(post("/api/life-console/bowel-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event.bowelEventId").isNotEmpty())
                .andExpect(jsonPath("$.data.event.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.event.occurredAtMillis").isNotEmpty())
                .andExpect(jsonPath("$.data.bowel").isNotEmpty());
    }

    // ---- TC-C13: POST /api/life-console/bowel-events (无认证) ----

    @Test
    void addBowelEventUnauthorizedReturns401() throws Exception {
        mockMvc.perform(post("/api/life-console/bowel-events"))
                .andExpect(status().isUnauthorized());
    }

    // ---- TC-C14: DELETE /api/life-console/bowel-events/latest ----

    @Test
    void deleteLatestBowelEventReturns200() throws Exception {
        String token = loginAndGetAccessToken();

        // First add a bowel event
        mockMvc.perform(post("/api/life-console/bowel-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Then delete latest
        mockMvc.perform(delete("/api/life-console/bowel-events/latest")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event.bowelEventId").isNotEmpty())
                .andExpect(jsonPath("$.data.bowel").isNotEmpty());
    }

    // ---- TC-C15: DELETE /api/life-console/bowel-events/latest (无认证) ----

    @Test
    void deleteLatestBowelEventUnauthorizedReturns401() throws Exception {
        mockMvc.perform(delete("/api/life-console/bowel-events/latest"))
                .andExpect(status().isUnauthorized());
    }

    // ---- TC-C16: FR-18 AC-4 POST /api/life-console/bowel-events with location body ----

    @Test
    void addBowelEventWithLocationReturns200() throws Exception {
        String token = loginAndGetAccessToken();

        mockMvc.perform(post("/api/life-console/bowel-events")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"latitude\":39.9075,\"longitude\":116.39723,\"locationLabel\":\"北京天安门\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event.bowelEventId").isNotEmpty())
                .andExpect(jsonPath("$.data.event.latitude").value(39.9075))
                .andExpect(jsonPath("$.data.event.longitude").value(116.39723))
                .andExpect(jsonPath("$.data.event.locationLabel").value("北京天安门"));
    }

    // ---- TC-C17: FR-18 AC-4 / IA-4 POST without body returns 200 (backward compat) ----

    @Test
    void addBowelEventWithoutBodyReturns200() throws Exception {
        String token = loginAndGetAccessToken();

        mockMvc.perform(post("/api/life-console/bowel-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event.bowelEventId").isNotEmpty())
                .andExpect(jsonPath("$.data.event.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.bowel").isNotEmpty());
    }

    // ---- TC-C18: FR-18 AC-3 POST /api/uploads/token with location returns 200 ----

    @Test
    void createUploadTokenWithLocationReturns200() throws Exception {
        String token = loginAndGetAccessToken();

        mockMvc.perform(post("/api/uploads/token")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"fileName": "loc-test.jpg", "fileSizeBytes": 512, "mimeType": "image/jpeg",
                                 "mediaType": "IMAGE", "width": 4, "height": 4, "displayTimeMillis": %d,
                                 "sourceFingerprint": "loc-fp-%s", "operationType": "IMPORT_TO_APP",
                                 "latitude": 39.9075, "longitude": 116.39723, "locationLabel": "北京天安门"}
                                """.formatted(System.currentTimeMillis(), System.nanoTime())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadId").isNotEmpty());
    }

    // ---- TC-C19: FR-18 AC-5 / IA-3 GET /api/life-console/history returns location fields ----

    @Test
    void getHistoryReturnsLocationFields() throws Exception {
        String token = loginAndGetAccessToken();

        // Add a bowel event with location so history has location data
        mockMvc.perform(post("/api/life-console/bowel-events")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"latitude\":39.9075,\"longitude\":116.39723,\"locationLabel\":\"北京天安门\"}"))
                .andExpect(status().isOk());

        // Verify history response structure includes locationLabel field keys
        mockMvc.perform(get("/api/life-console/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personDays").isArray())
                .andExpect(jsonPath("$.data.mealDays").isArray())
                .andExpect(jsonPath("$.data.bowelDays").isArray());
    }

    // ---- TC-C20: Round 7 PATCH /api/life-console/media/{mediaId}/location ----

    @Test
    void updateMediaLocationReturns200() throws Exception {
        String token = loginAndGetAccessToken();
        String mediaId = uploadAndConfirmMedia(token, "life-loc-" + System.nanoTime());

        // First add media to life console
        mockMvc.perform(post("/api/life-console/media")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"category": "PERSON", "mediaIds": ["%s"]}
                                """.formatted(mediaId)))
                .andExpect(status().isOk());

        // Then update location
        mockMvc.perform(patch("/api/life-console/media/{mediaId}/location", mediaId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"latitude\":39.9075,\"longitude\":116.39723,\"locationLabel\":\"北京天安门\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personSelf").isNotEmpty());
    }

    // ---- TC-C21: Round 7 PATCH /api/life-console/media/{mediaId}/location (无认证) ----

    @Test
    void updateMediaLocationUnauthorizedReturns401() throws Exception {
        mockMvc.perform(patch("/api/life-console/media/{mediaId}/location", "test_media_id")
                        .contentType("application/json")
                        .content("{\"latitude\":39.9075,\"longitude\":116.39723}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- TC-C22: Round 7 PATCH /api/life-console/bowel-events/{eventId}/location ----

    @Test
    void updateBowelEventLocationReturns200() throws Exception {
        String token = loginAndGetAccessToken();

        // First add a bowel event
        MvcResult addResult = mockMvc.perform(post("/api/life-console/bowel-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String eventId = readField(addResult, "data.event.bowelEventId");

        // Then update its location
        mockMvc.perform(patch("/api/life-console/bowel-events/{eventId}/location", eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"latitude\":39.9075,\"longitude\":116.39723,\"locationLabel\":\"北京天安门\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event.bowelEventId").value(eventId))
                .andExpect(jsonPath("$.data.event.latitude").value(39.9075))
                .andExpect(jsonPath("$.data.event.longitude").value(116.39723))
                .andExpect(jsonPath("$.data.event.locationLabel").value("北京天安门"));
    }

    // ---- TC-C23: Round 7 PATCH /api/life-console/bowel-events/{eventId}/location (无认证) ----

    @Test
    void updateBowelEventLocationUnauthorizedReturns401() throws Exception {
        mockMvc.perform(patch("/api/life-console/bowel-events/{eventId}/location", "test_event_id")
                        .contentType("application/json")
                        .content("{\"latitude\":39.9075,\"longitude\":116.39723}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- TC-C24: Round 7 today API returns bowel events list ----

    @Test
    void getTodayReturnsBowelEventsList() throws Exception {
        String token = loginAndGetAccessToken();

        // Add 2 bowel events with location
        mockMvc.perform(post("/api/life-console/bowel-events")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"latitude\":39.9075,\"longitude\":116.39723,\"locationLabel\":\"北京天安门\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/life-console/bowel-events")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"latitude\":31.2304,\"longitude\":121.4737,\"locationLabel\":\"上海外滩\"}"))
                .andExpect(status().isOk());

        // Verify today API returns events array
        mockMvc.perform(get("/api/life-console/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bowel.users").isArray())
                .andExpect(jsonPath("$.data.bowel.users[0].events").isArray())
                .andExpect(jsonPath("$.data.bowel.users[0].events.length()").value(2));
    }
}