package com.yingshi.server;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Upload three-phase integration tests against real PostgreSQL.
 * Covers: token creation, file upload, confirm, cancel, dismiss, history.
 */
class UploadIntegrationTest extends AbstractPostgresIntegrationTest {

    @Test
    void threePhaseUploadTokenFileConfirm() throws Exception {
        String token = loginAndGetAccessToken();

        // Phase 1: Create upload token
        MvcResult tokenResult = mockMvc.perform(post("/api/uploads/token")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"fileName": "test-upload.jpg", "fileSize": 1024, "mimeType": "image/jpeg",
                                 "sourceFingerprint": "test-fp-%s", "operationType": "IMPORT_TO_APP"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadId").isNotEmpty())
                .andReturn();
        String uploadId = readField(tokenResult, "data.uploadId");

        // Phase 2: Upload file
        MockMultipartFile file = new MockMultipartFile("file", "test-upload.jpg",
                "image/jpeg", jpegBytes());
        mockMvc.perform(multipart("/api/uploads/{uploadId}/file", uploadId)
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadId").value(uploadId));

        // Phase 3: Confirm
        mockMvc.perform(post("/api/uploads/{uploadId}/confirm", uploadId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("SUCCESS"));
    }

    @Test
    void cancelUpload() throws Exception {
        String token = loginAndGetAccessToken();

        MvcResult tokenResult = mockMvc.perform(post("/api/uploads/token")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"fileName": "cancel-test.jpg", "fileSize": 512, "mimeType": "image/jpeg",
                                 "sourceFingerprint": "cancel-fp-%s", "operationType": "IMPORT_TO_APP"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isOk())
                .andReturn();
        String uploadId = readField(tokenResult, "data.uploadId");

        mockMvc.perform(post("/api/uploads/{uploadId}/cancel", uploadId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("CANCELLED"));
    }

    @Test
    void uploadHistoryListing() throws Exception {
        String token = loginAndGetAccessToken();

        // Create a few upload tokens
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/uploads/token")
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .content("""
                                    {"fileName": "history-%d.jpg", "fileSize": 256, "mimeType": "image/jpeg",
                                     "sourceFingerprint": "hist-fp-%d", "operationType": "IMPORT_TO_APP"}
                                    """.formatted(i, System.nanoTime() + i)))
                    .andExpect(status().isOk());
        }

        // List history
        mockMvc.perform(get("/api/uploads")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void dismissUpload() throws Exception {
        String token = loginAndGetAccessToken();

        MvcResult tokenResult = mockMvc.perform(post("/api/uploads/token")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"fileName": "dismiss-test.jpg", "fileSize": 128, "mimeType": "image/jpeg",
                                 "sourceFingerprint": "dismiss-fp-%s", "operationType": "IMPORT_TO_APP"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isOk())
                .andReturn();
        String uploadId = readField(tokenResult, "data.uploadId");

        // Cancel then dismiss
        mockMvc.perform(post("/api/uploads/{uploadId}/cancel", uploadId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/uploads/{uploadId}/dismiss", uploadId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void duplicateFingerprintDetected() throws Exception {
        String token = loginAndGetAccessToken();
        String fingerprint = "dup-fp-" + System.nanoTime();

        // First upload
        MvcResult first = mockMvc.perform(post("/api/uploads/token")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"fileName": "dup1.jpg", "fileSize": 512, "mimeType": "image/jpeg",
                                 "sourceFingerprint": "%s", "operationType": "IMPORT_TO_APP"}
                                """.formatted(fingerprint)))
                .andExpect(status().isOk())
                .andReturn();
        String firstId = readField(first, "data.uploadId");

        MockMultipartFile file = new MockMultipartFile("file", "dup1.jpg",
                "image/jpeg", jpegBytes());
        mockMvc.perform(multipart("/api/uploads/{uploadId}/file", firstId)
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/uploads/{uploadId}/confirm", firstId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Second upload with same fingerprint should detect duplicate
        mockMvc.perform(post("/api/uploads/token")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"fileName": "dup2.jpg", "fileSize": 512, "mimeType": "image/jpeg",
                                 "sourceFingerprint": "%s", "operationType": "IMPORT_TO_APP"}
                                """.formatted(fingerprint)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicateMediaId").isNotEmpty());
    }
}
