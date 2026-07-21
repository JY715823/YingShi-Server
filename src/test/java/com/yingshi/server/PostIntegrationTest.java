package com.yingshi.server;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Small Album (Post) CRUD integration tests against real PostgreSQL.
 * Covers 8 API endpoints on /api/small-albums plus participantUserIds validation.
 */
class PostIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PARTICIPANT_USER_A = "user_demo_a";
    private static final String PARTICIPANT_USER_B = "user_demo_b";

    // ---- Helpers (copied from AlbumIntegrationTest) ----

    private String createAlbum(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/albums")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"title": "%s"}
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn();
        return readField(result, "data.albumId");
    }

    private String uploadAndConfirmMedia(String accessToken, String fingerprint) throws Exception {
        long displayTime = System.currentTimeMillis();
        MvcResult tokenResult = mockMvc.perform(post("/api/uploads/token")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("""
                                {"fileName": "post-test.jpg", "fileSizeBytes": 512, "mimeType": "image/jpeg",
                                 "mediaType": "IMAGE", "width": 4, "height": 4, "displayTimeMillis": %d,
                                 "sourceFingerprint": "%s", "operationType": "IMPORT_TO_APP"}
                                """.formatted(displayTime, fingerprint)))
                .andExpect(status().isOk())
                .andReturn();
        String uploadId = readField(tokenResult, "data.uploadId");

        MockMultipartFile file = new MockMultipartFile("file", "post-test.jpg",
                "image/jpeg", jpegBytes());
        mockMvc.perform(multipart("/api/uploads/{uploadId}/file", uploadId)
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        MvcResult confirmResult = mockMvc.perform(post("/api/uploads/{uploadId}/confirm", uploadId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return readField(confirmResult, "data.mediaId");
    }

    private String createSmallAlbum(String token, String albumId, String title, String mediaId) throws Exception {
        long displayTime = System.currentTimeMillis();
        MvcResult result = mockMvc.perform(post("/api/small-albums")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"albumId": "%s", "title": "%s", "participantUserIds": ["%s"],
                                 "displayTimeMillis": %d, "initialMediaIds": ["%s"]}
                                """.formatted(albumId, title, PARTICIPANT_USER_A, displayTime, mediaId)))
                .andExpect(status().isOk())
                .andReturn();
        return readField(result, "data.smallAlbumId");
    }

    // ---- Additional helpers ----

    private String jsonArrayOfStrings(String... values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(values[i]).append("\"");
        }
        return sb.append("]").toString();
    }

    private String createSmallAlbumWithMedia(String token, String albumId, String title, String... mediaIds) throws Exception {
        long displayTime = System.currentTimeMillis();
        String mediaIdsJson = jsonArrayOfStrings(mediaIds);
        MvcResult result = mockMvc.perform(post("/api/small-albums")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"albumId": "%s", "title": "%s", "participantUserIds": ["%s"],
                                 "displayTimeMillis": %d, "initialMediaIds": %s}
                                """.formatted(albumId, title, PARTICIPANT_USER_A, displayTime, mediaIdsJson)))
                .andExpect(status().isOk())
                .andReturn();
        return readField(result, "data.smallAlbumId");
    }

    // ---- AC-1: Endpoint coverage ----

    @Test
    void createSmallAlbum_success() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Post Create Album " + System.nanoTime());
        String mediaId = uploadAndConfirmMedia(token, "post-create-" + System.nanoTime());
        long displayTime = System.currentTimeMillis();

        mockMvc.perform(post("/api/small-albums")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"albumId": "%s", "title": "测试小相册", "summary": "测试简介",
                                 "participantUserIds": ["%s"],
                                 "displayTimeMillis": %d, "initialMediaIds": ["%s"],
                                 "coverMediaId": "%s"}
                                """.formatted(albumId, PARTICIPANT_USER_A, displayTime, mediaId, mediaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smallAlbumId").isNotEmpty())
                .andExpect(jsonPath("$.data.title").value("测试小相册"))
                .andExpect(jsonPath("$.data.participantUserIds").isArray())
                .andExpect(jsonPath("$.data.participantUserIds[0]").value(PARTICIPANT_USER_A));
    }

    @Test
    void getSmallAlbumDetail_success() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Detail Album " + System.nanoTime());
        String mediaId = uploadAndConfirmMedia(token, "post-detail-" + System.nanoTime());
        String smallAlbumId = createSmallAlbum(token, albumId, "Detail Small Album", mediaId);

        mockMvc.perform(get("/api/small-albums/{smallAlbumId}", smallAlbumId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smallAlbumId").value(smallAlbumId))
                .andExpect(jsonPath("$.data.mediaItems").isArray())
                .andExpect(jsonPath("$.data.mediaItems.length()").value(1));
    }

    @Test
    void listSmallAlbums_success() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "List Album " + System.nanoTime());
        String mediaId = uploadAndConfirmMedia(token, "post-list-" + System.nanoTime());
        String smallAlbumId = createSmallAlbum(token, albumId, "Listable Small Album", mediaId);

        mockMvc.perform(get("/api/small-albums")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.smallAlbumId == '" + smallAlbumId + "')]").exists());
    }

    @Test
    void updateSmallAlbum_success() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Update Album " + System.nanoTime());
        String mediaId = uploadAndConfirmMedia(token, "post-update-" + System.nanoTime());
        String smallAlbumId = createSmallAlbum(token, albumId, "Before Update", mediaId);

        mockMvc.perform(patch("/api/small-albums/{smallAlbumId}", smallAlbumId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"title": "After Update", "summary": "Updated summary"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("After Update"))
                .andExpect(jsonPath("$.data.summary").value("Updated summary"));
    }

    @Test
    void addMediaToPost_success() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Add Media Album " + System.nanoTime());
        String mediaId1 = uploadAndConfirmMedia(token, "post-add-1-" + System.nanoTime());
        String mediaId2 = uploadAndConfirmMedia(token, "post-add-2-" + System.nanoTime());
        String smallAlbumId = createSmallAlbum(token, albumId, "Add Media Small Album", mediaId1);

        mockMvc.perform(post("/api/small-albums/{smallAlbumId}/media", smallAlbumId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"mediaIds": ["%s"], "coverMediaId": null}
                                """.formatted(mediaId2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaItems").isArray())
                .andExpect(jsonPath("$.data.mediaItems.length()").value(2));
    }

    @Test
    void deleteSmallAlbum_success() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Delete Album " + System.nanoTime());
        String mediaId = uploadAndConfirmMedia(token, "post-delete-" + System.nanoTime());
        String smallAlbumId = createSmallAlbum(token, albumId, "To Delete Small Album", mediaId);

        mockMvc.perform(delete("/api/small-albums/{smallAlbumId}", smallAlbumId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trashItemId").isNotEmpty())
                .andExpect(jsonPath("$.data.itemType").value("smallAlbumDeleted"))
                .andExpect(jsonPath("$.data.state").value("inTrash"));
    }

    // ---- AC-2: participantUserIds validation ----

    @Test
    void createSmallAlbum_emptyParticipants_validationError() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Empty Part Album " + System.nanoTime());
        String mediaId = uploadAndConfirmMedia(token, "post-empty-part-" + System.nanoTime());
        long displayTime = System.currentTimeMillis();

        mockMvc.perform(post("/api/small-albums")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"albumId": "%s", "title": "Empty Participants",
                                 "participantUserIds": [],
                                 "displayTimeMillis": %d, "initialMediaIds": ["%s"]}
                                """.formatted(albumId, displayTime, mediaId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createSmallAlbum_singleParticipant_success() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Single Part Album " + System.nanoTime());
        String mediaId = uploadAndConfirmMedia(token, "post-single-part-" + System.nanoTime());
        long displayTime = System.currentTimeMillis();
        String participantsJson = jsonArrayOfStrings(PARTICIPANT_USER_A);
        String mediaIdsJson = jsonArrayOfStrings(mediaId);

        mockMvc.perform(post("/api/small-albums")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"albumId": "%s", "title": "Single Participant", "participantUserIds": %s,
                                 "displayTimeMillis": %d, "initialMediaIds": %s}
                                """.formatted(albumId, participantsJson, displayTime, mediaIdsJson)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smallAlbumId").isNotEmpty())
                .andExpect(jsonPath("$.data.participantUserIds").isArray())
                .andExpect(jsonPath("$.data.participantUserIds.length()").value(1))
                .andExpect(jsonPath("$.data.participantUserIds[0]").value(PARTICIPANT_USER_A));
    }

    @Test
    void createSmallAlbum_multipleParticipants_success() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Multi Part Album " + System.nanoTime());
        String mediaId = uploadAndConfirmMedia(token, "post-multi-part-" + System.nanoTime());
        long displayTime = System.currentTimeMillis();
        String participantsJson = jsonArrayOfStrings(PARTICIPANT_USER_A, PARTICIPANT_USER_B);
        String mediaIdsJson = jsonArrayOfStrings(mediaId);

        mockMvc.perform(post("/api/small-albums")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"albumId": "%s", "title": "Multiple Participants", "participantUserIds": %s,
                                 "displayTimeMillis": %d, "initialMediaIds": %s}
                                """.formatted(albumId, participantsJson, displayTime, mediaIdsJson)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smallAlbumId").isNotEmpty())
                .andExpect(jsonPath("$.data.participantUserIds").isArray())
                .andExpect(jsonPath("$.data.participantUserIds.length()").value(2))
                .andExpect(jsonPath("$.data.participantUserIds[0]").value(PARTICIPANT_USER_A))
                .andExpect(jsonPath("$.data.participantUserIds[1]").value(PARTICIPANT_USER_B));
    }

    @Test
    void createSmallAlbum_duplicateParticipants_deduplicated() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Dedup Album " + System.nanoTime());
        String mediaId = uploadAndConfirmMedia(token, "post-dedup-" + System.nanoTime());
        long displayTime = System.currentTimeMillis();
        String participantsJson = jsonArrayOfStrings(PARTICIPANT_USER_A, PARTICIPANT_USER_A, PARTICIPANT_USER_B);
        String mediaIdsJson = jsonArrayOfStrings(mediaId);

        mockMvc.perform(post("/api/small-albums")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"albumId": "%s", "title": "Deduplicated Participants", "participantUserIds": %s,
                                 "displayTimeMillis": %d, "initialMediaIds": %s}
                                """.formatted(albumId, participantsJson, displayTime, mediaIdsJson)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smallAlbumId").isNotEmpty())
                .andExpect(jsonPath("$.data.participantUserIds").isArray())
                .andExpect(jsonPath("$.data.participantUserIds.length()").value(2))
                .andExpect(jsonPath("$.data.participantUserIds[0]").value(PARTICIPANT_USER_A))
                .andExpect(jsonPath("$.data.participantUserIds[1]").value(PARTICIPANT_USER_B));
    }

    // ---- AC-3: addMediaToPost does not merge operator ----

    @Test
    void addMediaToPost_doesNotMergeOperatorToParticipants() throws Exception {
        String tokenA = loginAndGetAccessToken(ACCOUNT_A, TEMP_PASSWORD);
        String albumId = createAlbum(tokenA, "Operator Merge Album " + System.nanoTime());
        String mediaIdA = uploadAndConfirmMedia(tokenA, "post-merge-a-" + System.nanoTime());
        long displayTime = System.currentTimeMillis();

        MvcResult createResult = mockMvc.perform(post("/api/small-albums")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content("""
                                {"albumId": "%s", "title": "Operator Merge Small Album",
                                 "participantUserIds": ["%s"],
                                 "displayTimeMillis": %d, "initialMediaIds": ["%s"]}
                                """.formatted(albumId, PARTICIPANT_USER_A, displayTime, mediaIdA)))
                .andExpect(status().isOk())
                .andReturn();
        String smallAlbumId = readField(createResult, "data.smallAlbumId");

        String tokenB = loginAndGetAccessToken(ACCOUNT_B, TEMP_PASSWORD);
        String mediaIdB = uploadAndConfirmMedia(tokenB, "post-merge-b-" + System.nanoTime());

        mockMvc.perform(post("/api/small-albums/{smallAlbumId}/media", smallAlbumId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType("application/json")
                        .content("""
                                {"mediaIds": ["%s"], "coverMediaId": null}
                                """.formatted(mediaIdB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participantUserIds").isArray())
                .andExpect(jsonPath("$.data.participantUserIds.length()").value(1))
                .andExpect(jsonPath("$.data.participantUserIds[0]").value(PARTICIPANT_USER_A));
    }

    // ---- AC-4: Media order and cover ----

    @Test
    void updateSmallAlbumCover_success() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Cover Album " + System.nanoTime());
        String mediaId1 = uploadAndConfirmMedia(token, "post-cover-1-" + System.nanoTime());
        String mediaId2 = uploadAndConfirmMedia(token, "post-cover-2-" + System.nanoTime());
        String smallAlbumId = createSmallAlbumWithMedia(token, albumId, "Cover Small Album", mediaId1, mediaId2);

        mockMvc.perform(patch("/api/small-albums/{smallAlbumId}/cover", smallAlbumId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"coverMediaId": "%s"}
                                """.formatted(mediaId2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverMediaId").value(mediaId2));
    }

    @Test
    void updateSmallAlbumCover_invalidMediaId_validationError() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Invalid Cover Album " + System.nanoTime());
        String mediaId = uploadAndConfirmMedia(token, "post-invalid-cover-" + System.nanoTime());
        String smallAlbumId = createSmallAlbum(token, albumId, "Invalid Cover Small Album", mediaId);

        mockMvc.perform(patch("/api/small-albums/{smallAlbumId}/cover", smallAlbumId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"coverMediaId": "non-existent-media-id"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SMALL_ALBUM_COVER_INVALID"));
    }

    @Test
    void updateSmallAlbumMediaOrder_success() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Order Album " + System.nanoTime());
        String mediaId1 = uploadAndConfirmMedia(token, "post-order-1-" + System.nanoTime());
        String mediaId2 = uploadAndConfirmMedia(token, "post-order-2-" + System.nanoTime());
        String smallAlbumId = createSmallAlbumWithMedia(token, albumId, "Order Small Album", mediaId1, mediaId2);

        String orderedMediaIdsJson = jsonArrayOfStrings(mediaId2, mediaId1);
        mockMvc.perform(patch("/api/small-albums/{smallAlbumId}/media-order", smallAlbumId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"orderedMediaIds": %s}
                                """.formatted(orderedMediaIdsJson)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaItems").isArray())
                .andExpect(jsonPath("$.data.mediaItems[0].media.mediaId").value(mediaId2))
                .andExpect(jsonPath("$.data.mediaItems[1].media.mediaId").value(mediaId1));
    }

    @Test
    void updateSmallAlbumMediaOrder_incompleteMediaIds_validationError() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Incomplete Order Album " + System.nanoTime());
        String mediaId1 = uploadAndConfirmMedia(token, "post-incomplete-1-" + System.nanoTime());
        String mediaId2 = uploadAndConfirmMedia(token, "post-incomplete-2-" + System.nanoTime());
        String smallAlbumId = createSmallAlbumWithMedia(token, albumId, "Incomplete Order Small Album", mediaId1, mediaId2);

        String orderedMediaIdsJson = jsonArrayOfStrings(mediaId1);
        mockMvc.perform(patch("/api/small-albums/{smallAlbumId}/media-order", smallAlbumId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"orderedMediaIds": %s}
                                """.formatted(orderedMediaIdsJson)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SMALL_ALBUM_MEDIA_ORDER_INVALID"));
    }

    // ---- FR-5: Batch remove media (PATCH /{smallAlbumId}/media-batch) ----

    @Test
    void batchRemoveMedia_success() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Batch Remove Album " + System.nanoTime());
        String mediaId1 = uploadAndConfirmMedia(token, "post-batch-1-" + System.nanoTime());
        String mediaId2 = uploadAndConfirmMedia(token, "post-batch-2-" + System.nanoTime());
        String mediaId3 = uploadAndConfirmMedia(token, "post-batch-3-" + System.nanoTime());
        String smallAlbumId = createSmallAlbumWithMedia(token, albumId, "Batch Remove Small Album", mediaId1, mediaId2, mediaId3);

        String removeIdsJson = jsonArrayOfStrings(mediaId1, mediaId2);
        mockMvc.perform(patch("/api/small-albums/{smallAlbumId}/media-batch", smallAlbumId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"removeMediaIds": %s}
                                """.formatted(removeIdsJson)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaItems").isArray())
                .andExpect(jsonPath("$.data.mediaItems.length()").value(1))
                .andExpect(jsonPath("$.data.mediaItems[0].media.mediaId").value(mediaId3));
    }

    @Test
    void batchRemoveMedia_coverCompensation() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Batch Cover Album " + System.nanoTime());
        String mediaId1 = uploadAndConfirmMedia(token, "post-batch-cover-1-" + System.nanoTime());
        String mediaId2 = uploadAndConfirmMedia(token, "post-batch-cover-2-" + System.nanoTime());
        long displayTime = System.currentTimeMillis();
        String mediaIdsJson = jsonArrayOfStrings(mediaId1, mediaId2);
        MvcResult createResult = mockMvc.perform(post("/api/small-albums")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"albumId": "%s", "title": "Batch Cover Small Album", "participantUserIds": ["%s"],
                                 "displayTimeMillis": %d, "initialMediaIds": %s, "coverMediaId": "%s"}
                                """.formatted(albumId, PARTICIPANT_USER_A, displayTime, mediaIdsJson, mediaId1)))
                .andExpect(status().isOk())
                .andReturn();
        String smallAlbumId = readField(createResult, "data.smallAlbumId");

        String removeIdsJson = jsonArrayOfStrings(mediaId1);
        mockMvc.perform(patch("/api/small-albums/{smallAlbumId}/media-batch", smallAlbumId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"removeMediaIds": %s}
                                """.formatted(removeIdsJson)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverMediaId").value(mediaId2));
    }

    @Test
    void batchRemoveMedia_duplicateIds_validationError() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Batch Dup Album " + System.nanoTime());
        String mediaId1 = uploadAndConfirmMedia(token, "post-batch-dup-1-" + System.nanoTime());
        String mediaId2 = uploadAndConfirmMedia(token, "post-batch-dup-2-" + System.nanoTime());
        String smallAlbumId = createSmallAlbumWithMedia(token, albumId, "Batch Dup Small Album", mediaId1, mediaId2);

        String removeIdsJson = jsonArrayOfStrings(mediaId1, mediaId1);
        mockMvc.perform(patch("/api/small-albums/{smallAlbumId}/media-batch", smallAlbumId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"removeMediaIds": %s}
                                """.formatted(removeIdsJson)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SMALL_ALBUM_MEDIA_ORDER_INVALID"));
    }

    @Test
    void batchRemoveMedia_foreignMediaId_validationError() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Batch Foreign Album " + System.nanoTime());
        String mediaId1 = uploadAndConfirmMedia(token, "post-batch-foreign-1-" + System.nanoTime());
        String mediaId2 = uploadAndConfirmMedia(token, "post-batch-foreign-2-" + System.nanoTime());
        String smallAlbumId = createSmallAlbumWithMedia(token, albumId, "Batch Foreign Small Album", mediaId1);
        // mediaId2 不属于该小相册

        String removeIdsJson = jsonArrayOfStrings(mediaId2);
        mockMvc.perform(patch("/api/small-albums/{smallAlbumId}/media-batch", smallAlbumId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"removeMediaIds": %s}
                                """.formatted(removeIdsJson)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    void batchRemoveMedia_wouldEmpty_conflict() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Batch Empty Album " + System.nanoTime());
        String mediaId1 = uploadAndConfirmMedia(token, "post-batch-empty-1-" + System.nanoTime());
        String mediaId2 = uploadAndConfirmMedia(token, "post-batch-empty-2-" + System.nanoTime());
        String smallAlbumId = createSmallAlbumWithMedia(token, albumId, "Batch Empty Small Album", mediaId1, mediaId2);

        String removeIdsJson = jsonArrayOfStrings(mediaId1, mediaId2);
        mockMvc.perform(patch("/api/small-albums/{smallAlbumId}/media-batch", smallAlbumId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"removeMediaIds": %s}
                                """.formatted(removeIdsJson)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DELETE_CONFLICT"));
    }

    @Test
    void batchRemoveMedia_postNotFound() throws Exception {
        String token = loginAndGetAccessToken();
        String removeIdsJson = jsonArrayOfStrings("non-existent-media");
        mockMvc.perform(patch("/api/small-albums/{smallAlbumId}/media-batch", "non-existent-post-id")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"removeMediaIds": %s}
                                """.formatted(removeIdsJson)))
                .andExpect(status().isNotFound());
    }

    @Test
    void batchRemoveMedia_sortOrderResequence() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Batch Reseq Album " + System.nanoTime());
        String mediaId1 = uploadAndConfirmMedia(token, "post-batch-reseq-1-" + System.nanoTime());
        String mediaId2 = uploadAndConfirmMedia(token, "post-batch-reseq-2-" + System.nanoTime());
        String mediaId3 = uploadAndConfirmMedia(token, "post-batch-reseq-3-" + System.nanoTime());
        String mediaId4 = uploadAndConfirmMedia(token, "post-batch-reseq-4-" + System.nanoTime());
        String smallAlbumId = createSmallAlbumWithMedia(token, albumId, "Batch Reseq Small Album", mediaId1, mediaId2, mediaId3, mediaId4);

        String removeIdsJson = jsonArrayOfStrings(mediaId2, mediaId4);
        mockMvc.perform(patch("/api/small-albums/{smallAlbumId}/media-batch", smallAlbumId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"removeMediaIds": %s}
                                """.formatted(removeIdsJson)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaItems").isArray())
                .andExpect(jsonPath("$.data.mediaItems.length()").value(2))
                .andExpect(jsonPath("$.data.mediaItems[0].media.mediaId").value(mediaId1))
                .andExpect(jsonPath("$.data.mediaItems[1].media.mediaId").value(mediaId3));
    }
}
