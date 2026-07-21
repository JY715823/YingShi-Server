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
        long displayTime = System.currentTimeMillis();
        MvcResult tokenResult = mockMvc.perform(post("/api/uploads/token")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("""
                                {"fileName": "trash-test.jpg", "fileSizeBytes": 512, "mimeType": "image/jpeg",
                                 "mediaType": "IMAGE", "width": 4, "height": 4, "displayTimeMillis": %d,
                                 "sourceFingerprint": "%s", "operationType": "IMPORT_TO_APP"}
                                """.formatted(displayTime, fingerprint)))
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
    void listTrashReturnsValidPage() throws Exception {
        String token = loginAndGetAccessToken();
        mockMvc.perform(get("/api/trash/items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page").isNumber())
                .andExpect(jsonPath("$.data.size").isNumber())
                .andExpect(jsonPath("$.data.totalElements").isNumber())
                .andExpect(jsonPath("$.data.hasMore").isBoolean());
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
                .andExpect(jsonPath("$.data.items").isNotEmpty());

        // Restore
        mockMvc.perform(post("/api/trash/items/{trashItemId}/restore", trashItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("restored"));
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
                .andExpect(jsonPath("$.data.state").value("inTrash"));
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

    // ---- Large album deleted lifecycle tests (FR-1 AC-2/3/4) ----

    private static final String PARTICIPANT_USER_A = "user_demo_a";

    private String createLargeAlbum(String token, String title) throws Exception {
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

    private void createSmallAlbum(String token, String albumId, String title, String mediaId) throws Exception {
        long displayTime = System.currentTimeMillis();
        mockMvc.perform(post("/api/small-albums")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"albumId": "%s", "title": "%s", "participantUserIds": ["%s"],
                                 "displayTimeMillis": %d, "initialMediaIds": ["%s"]}
                                """.formatted(albumId, title, PARTICIPANT_USER_A, displayTime, mediaId)))
                .andExpect(status().isOk());
    }

    @Test
    void largeAlbumDeletedFullLifecycle() throws Exception {
        String token = loginAndGetAccessToken();
        // 1. Create large album + 2 small albums
        String albumId = createLargeAlbum(token, "Lifecycle Album " + System.nanoTime());
        String mediaId1 = uploadAndConfirmMedia(token, "lifecycle-1-" + System.nanoTime());
        String mediaId2 = uploadAndConfirmMedia(token, "lifecycle-2-" + System.nanoTime());
        createSmallAlbum(token, albumId, "Small A", mediaId1);
        createSmallAlbum(token, albumId, "Small B", mediaId2);

        // 2. Delete large album
        MvcResult deleteResult = mockMvc.perform(delete("/api/albums/{albumId}", albumId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String trashItemId = readField(deleteResult, "data.trashItemId");

        // 3. Trash list visible
        mockMvc.perform(get("/api/trash/items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.itemType == 'largeAlbumDeleted')]").exists());

        // 4. Restore
        mockMvc.perform(post("/api/trash/items/{trashItemId}/restore", trashItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("restored"));

        // 5. Verify albums + small albums restored
        mockMvc.perform(get("/api/albums/{albumId}/small-albums", albumId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // 6. Re-delete then purge for physical deletion
        MvcResult reDelete = mockMvc.perform(delete("/api/albums/{albumId}", albumId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String trashItemId2 = readField(reDelete, "data.trashItemId");
        mockMvc.perform(post("/api/trash/items/{trashItemId}/purge", trashItemId2)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 7. Verify physical deletion
        mockMvc.perform(get("/api/albums")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.albumId == '%s')]".formatted(albumId)).doesNotExist());
    }

    @Test
    void largeAlbumDeletedRestoreConflictDoesNotError() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createLargeAlbum(token, "Conflict Album " + System.nanoTime());

        // Delete
        MvcResult deleteResult = mockMvc.perform(delete("/api/albums/{albumId}", albumId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String trashItemId = readField(deleteResult, "data.trashItemId");

        // Restore
        mockMvc.perform(post("/api/trash/items/{trashItemId}/restore", trashItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("restored"));

        // Verify original album is restored and visible
        mockMvc.perform(get("/api/albums")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.albumId == '%s')]".formatted(albumId)).exists());
    }

    @Test
    void largeAlbumDeletedCascadeRestoreRestoresSmallAlbums() throws Exception {
        String token = loginAndGetAccessToken();
        // 1. Create large album + 3 small albums
        String albumId = createLargeAlbum(token, "Cascade Album " + System.nanoTime());
        String m1 = uploadAndConfirmMedia(token, "cascade-1-" + System.nanoTime());
        String m2 = uploadAndConfirmMedia(token, "cascade-2-" + System.nanoTime());
        String m3 = uploadAndConfirmMedia(token, "cascade-3-" + System.nanoTime());
        createSmallAlbum(token, albumId, "Small 1", m1);
        createSmallAlbum(token, albumId, "Small 2", m2);
        createSmallAlbum(token, albumId, "Small 3", m3);

        // 2. Delete large album
        MvcResult deleteResult = mockMvc.perform(delete("/api/albums/{albumId}", albumId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String trashItemId = readField(deleteResult, "data.trashItemId");

        // 3. Verify large album is soft-deleted (endpoint returns 404 because album is deleted)
        mockMvc.perform(get("/api/albums/{albumId}/small-albums", albumId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        // 4. Restore large album
        mockMvc.perform(post("/api/trash/items/{trashItemId}/restore", trashItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 5. Verify all 3 small albums restored
        mockMvc.perform(get("/api/albums/{albumId}/small-albums", albumId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    // ---- FR-9: Purge cascade deletes child media files + DB records ----

    @Test
    void purgeSmallAlbumCascadesMediaDeletion() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createLargeAlbum(token, "Purge Small Cascade " + System.nanoTime());
        String mediaId = uploadAndConfirmMedia(token, "purge-small-cascade-" + System.nanoTime());
        createSmallAlbum(token, albumId, "Small Cascade", mediaId);

        // Delete the small album
        MvcResult smallAlbumListResult = mockMvc.perform(get("/api/albums/{albumId}/small-albums", albumId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String smallAlbumId = readField(smallAlbumListResult, "data[0].smallAlbumId");

        MvcResult deleteResult = mockMvc.perform(delete("/api/small-albums/{smallAlbumId}", smallAlbumId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String trashItemId = readField(deleteResult, "data.trashItemId");

        // Purge
        mockMvc.perform(post("/api/trash/items/{trashItemId}/purge", trashItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Verify media file is gone (media was only in this small album, so cascade should delete it)
        mockMvc.perform(get("/api/media/files/{mediaId}", mediaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void purgeLargeAlbumCascadesMediaDeletion() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createLargeAlbum(token, "Purge Large Cascade " + System.nanoTime());
        String mediaId1 = uploadAndConfirmMedia(token, "purge-large-1-" + System.nanoTime());
        String mediaId2 = uploadAndConfirmMedia(token, "purge-large-2-" + System.nanoTime());
        createSmallAlbum(token, albumId, "Small A", mediaId1);
        createSmallAlbum(token, albumId, "Small B", mediaId2);

        // Delete large album
        MvcResult deleteResult = mockMvc.perform(delete("/api/albums/{albumId}", albumId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String trashItemId = readField(deleteResult, "data.trashItemId");

        // Purge
        mockMvc.perform(post("/api/trash/items/{trashItemId}/purge", trashItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Verify both media files are gone
        mockMvc.perform(get("/api/media/files/{mediaId}", mediaId1)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/media/files/{mediaId}", mediaId2)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void purgeSmallAlbumKeepsMediaReferencedByOtherAlbums() throws Exception {
        String token = loginAndGetAccessToken();
        String albumId = createLargeAlbum(token, "Shared Media Album " + System.nanoTime());
        String mediaId = uploadAndConfirmMedia(token, "shared-media-" + System.nanoTime());

        // Add the same media to two small albums
        createSmallAlbum(token, albumId, "Small A", mediaId);
        createSmallAlbum(token, albumId, "Small B", mediaId);

        // Get both small album IDs
        MvcResult listResult = mockMvc.perform(get("/api/albums/{albumId}/small-albums", albumId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String smallAlbumA = readField(listResult, "data[0].smallAlbumId");
        String smallAlbumB = readField(listResult, "data[1].smallAlbumId");

        // Delete + purge small album A
        MvcResult deleteResult = mockMvc.perform(delete("/api/small-albums/{smallAlbumId}", smallAlbumA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String trashItemId = readField(deleteResult, "data.trashItemId");
        mockMvc.perform(post("/api/trash/items/{trashItemId}/purge", trashItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Media should still be accessible because it's still referenced by small album B
        mockMvc.perform(get("/api/media/files/{mediaId}", mediaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ---- FR-10: Restore with fingerprint conflict migrates post_media relations ----

    @Test
    void restoreMediaSystemDeletedWithFingerprintConflictMigratesRelations() throws Exception {
        String token = loginAndGetAccessToken();
        String sharedFingerprint = "conflict-restore-" + System.nanoTime();
        String albumId = createLargeAlbum(token, "Conflict Restore Album " + System.nanoTime());

        // 1. Upload media1 with fingerprint F1
        String mediaId1 = uploadAndConfirmMedia(token, sharedFingerprint);

        // 2. Create small album with media1
        createSmallAlbum(token, albumId, "Conflict Small", mediaId1);

        // 3. Get small album ID
        MvcResult listResult = mockMvc.perform(get("/api/albums/{albumId}/small-albums", albumId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String smallAlbumId = readField(listResult, "data[0].smallAlbumId");

        // 4. System-delete media1 -> trash item created, post_media deleted
        MvcResult deleteResult = mockMvc.perform(delete("/api/media/{mediaId}", mediaId1)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String trashItemId = readField(deleteResult, "data.trashItemId");

        // 5. Upload media2 with the SAME fingerprint (media1 is soft-deleted, so no dedup conflict)
        String mediaId2 = uploadAndConfirmMedia(token, sharedFingerprint);

        // 6. Restore media1's trash item -> fingerprint conflict with media2
        mockMvc.perform(post("/api/trash/items/{trashItemId}/restore", trashItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("restored"));

        // 7. Verify media2 (the surviving active media) appears in the small album
        // The small album should have exactly 1 media item, and it should be media2
        mockMvc.perform(get("/api/small-albums/{smallAlbumId}", smallAlbumId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaCount").value(1))
                .andExpect(jsonPath("$.data.mediaItems[0].media.mediaId").value(mediaId2));
    }
}
