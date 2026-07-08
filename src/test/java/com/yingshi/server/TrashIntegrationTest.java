package com.yingshi.server;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Trash lifecycle integration tests against real PostgreSQL.
 * Covers: soft delete, restore, move-to-pending-cleanup, purge, undo-remove.
 */
class TrashIntegrationTest extends AbstractPostgresIntegrationTest {

    private String uploadAndConfirmMedia(String accessToken, String fingerprint) throws Exception {
        MvcResult tokenResult = mockMvc.perform(post("/api/uploads/token")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("""
                                {"fileName": "trash-test.jpg", "fileSize": 512, "mimeType": "image/jpeg",
                                 "sourceFingerprint": "%s", "operationType": "IMPORT_TO_APP"}
                                """.formatted(fingerprint)))
                .andExpect(status().isOk())
                .andReturn();
        String uploadId = readField(tokenResult, "data.uploadId");

        MockMultipartFile file = new MockMultipartFile("file", "trash-test.jpg",
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

    @Test
    void listTrashInitiallyEmpty() throws Exception {
        String token = loginAndGetAccessToken();
        mockMvc.perform(get("/api/trash/items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    void pendingCleanupInitiallyEmpty() throws Exception {
        String token = loginAndGetAccessToken();
        mockMvc.perform(get("/api/trash/pending-cleanup")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void systemDeleteCreatesTrashItemAndRestoreWorks() throws Exception {
        String token = loginAndGetAccessToken();
        String mediaId = uploadAndConfirmMedia(token, "sys-del-" + System.nanoTime());

        // System delete (from system media tool)
        MvcResult deleteResult = mockMvc.perform(delete("/api/media/{mediaId}", mediaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String trashItemId = readField(deleteResult, "data.trashItemId");

        // Verify trash item appears
        mockMvc.perform(get("/api/trash/items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isNotEmpty());

        // Restore
        mockMvc.perform(post("/api/trash/items/{trashItemId}/restore", trashItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("RESTORED"));
    }

    @Test
    void moveToPendingCleanupAndPurge() throws Exception {
        String token = loginAndGetAccessToken();
        String mediaId = uploadAndConfirmMedia(token, "purge-" + System.nanoTime());

        // System delete
        MvcResult deleteResult = mockMvc.perform(delete("/api/media/{mediaId}", mediaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String trashItemId = readField(deleteResult, "data.trashItemId");

        // Move to pending cleanup
        mockMvc.perform(post("/api/trash/items/{trashItemId}/remove", trashItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Verify pending cleanup list
        mockMvc.perform(get("/api/trash/pending-cleanup")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty());

        // Purge
        mockMvc.perform(post("/api/trash/items/{trashItemId}/purge", trashItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void undoRemoveReturnsToInTrash() throws Exception {
        String token = loginAndGetAccessToken();
        String mediaId = uploadAndConfirmMedia(token, "undo-" + System.nanoTime());

        // Delete → remove → undo
        MvcResult deleteResult = mockMvc.perform(delete("/api/media/{mediaId}", mediaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String trashItemId = readField(deleteResult, "data.trashItemId");

        mockMvc.perform(post("/api/trash/items/{trashItemId}/remove", trashItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/trash/items/{trashItemId}/undo-remove", trashItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("IN_TRASH"));
    }

    @Test
    void purgeDirectlyFromInTrash() throws Exception {
        String token = loginAndGetAccessToken();
        String mediaId = uploadAndConfirmMedia(token, "direct-purge-" + System.nanoTime());

        MvcResult deleteResult = mockMvc.perform(delete("/api/media/{mediaId}", mediaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String trashItemId = readField(deleteResult, "data.trashItemId");

        // Purge directly from IN_TRASH state (bypass pending cleanup)
        mockMvc.perform(post("/api/trash/items/{trashItemId}/purge", trashItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
