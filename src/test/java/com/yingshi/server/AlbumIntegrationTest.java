package com.yingshi.server;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Album CRUD integration tests against real PostgreSQL.
 * Covers 5 endpoints: create, list, getPosts, update(rename), delete.
 */
class AlbumIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PARTICIPANT_USER_A = "user_demo_a";

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
                                {"fileName": "album-test.jpg", "fileSizeBytes": 512, "mimeType": "image/jpeg",
                                 "mediaType": "IMAGE", "width": 4, "height": 4, "displayTimeMillis": %d,
                                 "sourceFingerprint": "%s", "operationType": "IMPORT_TO_APP"}
                                """.formatted(displayTime, fingerprint)))
                .andExpect(status().isOk())
                .andReturn();
        String uploadId = readField(tokenResult, "data.uploadId");

        MockMultipartFile file = new MockMultipartFile("file", "album-test.jpg",
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

    @Test
    void createAlbumReturnsAlbumDto() throws Exception {
        String token = loginAndGetAccessToken();
        mockMvc.perform(post("/api/albums")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"title": "Test Album", "subtitle": "Test Subtitle"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.albumId").isNotEmpty())
                .andExpect(jsonPath("$.data.title").value("Test Album"))
                .andExpect(jsonPath("$.data.subtitle").value("Test Subtitle"))
                .andExpect(jsonPath("$.data.smallAlbumCount").value(0));
    }

    @Test
    void listAlbumsReturnsCreatedAlbum() throws Exception {
        String token = loginAndGetAccessToken();
        String uniqueTitle = "Listable Album " + System.nanoTime();
        createAlbum(token, uniqueTitle);

        mockMvc.perform(get("/api/albums")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.title == '%s')]".formatted(uniqueTitle)).exists());
    }

    @Test
    void listAlbumPostsReturnsSmallAlbums() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Parent Album " + System.nanoTime());
        String mediaId = uploadAndConfirmMedia(token, "album-post-" + System.nanoTime());
        createSmallAlbum(token, albumId, "Child Small Album", mediaId);

        mockMvc.perform(get("/api/albums/{albumId}/small-albums", albumId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("Child Small Album"))
                .andExpect(jsonPath("$.data[0].albumId").value(albumId));
    }

    @Test
    void updateAlbumRenamesTitle() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Before Rename " + System.nanoTime());
        mockMvc.perform(patch("/api/albums/{albumId}", albumId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"title": "After Rename", "subtitle": "Updated Subtitle"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("After Rename"))
                .andExpect(jsonPath("$.data.subtitle").value("Updated Subtitle"));
    }

    @Test
    void deleteAlbumReturnsTrashItem() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "To Delete " + System.nanoTime());
        mockMvc.perform(delete("/api/albums/{albumId}", albumId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trashItemId").isNotEmpty())
                .andExpect(jsonPath("$.data.itemType").value("largeAlbumDeleted"))
                .andExpect(jsonPath("$.data.state").value("inTrash"));
    }

    @Test
    void moveSmallAlbumsSwitchesAlbum() throws Exception {
        String token = loginAndGetAccessToken();
        String albumAId = createAlbum(token, "Source Album " + System.nanoTime());
        String albumBId = createAlbum(token, "Target Album " + System.nanoTime());
        String mediaId = uploadAndConfirmMedia(token, "move-test-" + System.nanoTime());
        String smallAlbumId = createSmallAlbum(token, albumAId, "Movable Small Album", mediaId);

        // Move small album from albumA to albumB
        mockMvc.perform(patch("/api/albums/{targetAlbumId}/move-small-albums", albumBId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"smallAlbumIds": ["%s"]}
                                """.formatted(smallAlbumId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].smallAlbumId").value(smallAlbumId))
                .andExpect(jsonPath("$.data[0].albumId").value(albumBId));

        // Verify albumA has no small albums
        mockMvc.perform(get("/api/albums/{albumId}/small-albums", albumAId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        // Verify albumB has the moved small album
        mockMvc.perform(get("/api/albums/{albumId}/small-albums", albumBId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].smallAlbumId").value(smallAlbumId))
                .andExpect(jsonPath("$.data[0].albumId").value(albumBId));

        // Verify album counts updated
        mockMvc.perform(get("/api/albums")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.albumId == '%s')].smallAlbumCount".formatted(albumAId)).value(0))
                .andExpect(jsonPath("$.data[?(@.albumId == '%s')].smallAlbumCount".formatted(albumBId)).value(1));
    }

    @Test
    void moveSmallAlbumsRejectsMissingTargetAlbum() throws Exception {
        String token = loginAndGetAccessToken();
        mockMvc.perform(patch("/api/albums/{targetAlbumId}/move-small-albums", "nonexistent-album-id")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"smallAlbumIds": ["some-id"]}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void moveSmallAlbumsRejectsEmptyRequest() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Target For Empty " + System.nanoTime());
        mockMvc.perform(patch("/api/albums/{targetAlbumId}/move-small-albums", albumId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"smallAlbumIds": []}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moveSmallAlbumsRejectsMissingSmallAlbum() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createAlbum(token, "Target For Missing " + System.nanoTime());
        mockMvc.perform(patch("/api/albums/{targetAlbumId}/move-small-albums", albumId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"smallAlbumIds": ["nonexistent-small-album-id"]}
                                """))
                .andExpect(status().isNotFound());
    }
}
