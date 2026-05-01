package com.yingshi.server;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class YingshiServerApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void healthEndpointReturnsApiResponse() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.application").value("yingshi-server"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void authFlowWorksForSeededUser() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account": "demo.a@yingshi.local",
                                  "password": "demo123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("user_demo_a"))
                .andExpect(jsonPath("$.data.spaceId").value("space_demo_shared"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        String accessToken = readField(loginResult, "/data/accessToken");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("user_demo_a"))
                .andExpect(jsonPath("$.data.account").value("demo.a@yingshi.local"))
                .andExpect(jsonPath("$.data.spaceId").value("space_demo_shared"));
    }

    @Test
    void protectedEndpointRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void openApiDocsRemainAccessibleInDev() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void contentReadApisWorkForCurrentSpace() throws Exception {
        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(get("/api/albums")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        mockMvc.perform(get("/api/albums/album_001/posts")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].postId").value("post_001"))
                .andExpect(jsonPath("$.data[1].postId").value("post_002"));

        mockMvc.perform(get("/api/posts/post_001")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value("post_001"))
                .andExpect(jsonPath("$.data.coverMediaId").value("media_001"))
                .andExpect(jsonPath("$.data.mediaItems.length()").value(3))
                .andExpect(jsonPath("$.data.mediaItems[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.mediaItems[0].media.mediaId").value("media_001"));

        mockMvc.perform(get("/api/media/feed")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[0].mediaId").value("media_001"))
                .andExpect(jsonPath("$.data[0].postIds.length()").value(2));
    }

    @Test
    void contentMutationApisWorkForCurrentSpace() throws Exception {
        String accessToken = loginAndGetAccessToken();

        MvcResult createResult = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Fresh Layout",
                                  "summary": "Built from seeded media",
                                  "contributorLabel": "Demo A",
                                  "displayTimeMillis": 1777413000000,
                                  "albumIds": ["album_001", "album_003"],
                                  "initialMediaIds": ["media_003", "media_005"],
                                  "coverMediaId": "media_005"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Fresh Layout"))
                .andExpect(jsonPath("$.data.albumIds.length()").value(2))
                .andExpect(jsonPath("$.data.coverMediaId").value("media_005"))
                .andReturn();

        String postId = readField(createResult, "/data/postId");

        mockMvc.perform(patch("/api/posts/" + postId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Fresh Layout Updated",
                                  "summary": "Updated summary",
                                  "albumIds": ["album_002"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Fresh Layout Updated"))
                .andExpect(jsonPath("$.data.albumIds.length()").value(1))
                .andExpect(jsonPath("$.data.albumIds[0]").value("album_002"));

        mockMvc.perform(patch("/api/posts/" + postId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Fresh Layout Stable",
                                  "summary": "Updated summary again",
                                  "albumIds": ["album_002"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Fresh Layout Stable"))
                .andExpect(jsonPath("$.data.albumIds.length()").value(1))
                .andExpect(jsonPath("$.data.albumIds[0]").value("album_002"));

        mockMvc.perform(patch("/api/posts/" + postId + "/cover")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "coverMediaId": "media_003"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverMediaId").value("media_003"));

        mockMvc.perform(patch("/api/posts/" + postId + "/media-order")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderedMediaIds": ["media_005", "media_003"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaItems[0].media.mediaId").value("media_005"))
                .andExpect(jsonPath("$.data.mediaItems[1].media.mediaId").value("media_003"));
    }

    @Test
    void currentUserCannotAccessOtherSpacePost() throws Exception {
        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(get("/api/posts/post_other_secret")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    @Test
    void commentApisWorkAndPostMediaFlowsStaySeparated() throws Exception {
        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(get("/api/posts/post_001/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments.length()").value(2))
                .andExpect(jsonPath("$.data.comments[0].targetType").value("POST"))
                .andExpect(jsonPath("$.data.comments[0].postId").value("post_001"))
                .andExpect(jsonPath("$.data.comments[0].mediaId").value(nullValue()));

        mockMvc.perform(get("/api/media/media_001/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments.length()").value(2))
                .andExpect(jsonPath("$.data.comments[0].targetType").value("MEDIA"))
                .andExpect(jsonPath("$.data.comments[0].mediaId").value("media_001"))
                .andExpect(jsonPath("$.data.comments[0].postId").value(nullValue()));

        MvcResult postCommentResult = mockMvc.perform(post("/api/posts/post_001/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "New post comment"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetType").value("POST"))
                .andExpect(jsonPath("$.data.postId").value("post_001"))
                .andExpect(jsonPath("$.data.mediaId").value(nullValue()))
                .andReturn();

        MvcResult mediaCommentResult = mockMvc.perform(post("/api/media/media_001/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "New media comment"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetType").value("MEDIA"))
                .andExpect(jsonPath("$.data.mediaId").value("media_001"))
                .andExpect(jsonPath("$.data.postId").value(nullValue()))
                .andReturn();

        String postCommentId = readField(postCommentResult, "/data/commentId");
        String mediaCommentId = readField(mediaCommentResult, "/data/commentId");

        mockMvc.perform(patch("/api/comments/" + postCommentId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Updated post comment"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Updated post comment"));

        mockMvc.perform(delete("/api/comments/" + mediaCommentId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDeleted").value(true))
                .andExpect(jsonPath("$.data.content").value(nullValue()));

        mockMvc.perform(get("/api/media/media_001/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments[0].commentId").value(mediaCommentId))
                .andExpect(jsonPath("$.data.comments[0].isDeleted").value(true));
    }

    @Test
    void sameSpaceMemberCanEditOtherCommentAndOtherSpaceStillBlocked() throws Exception {
        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(patch("/api/comments/comment_post_002")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Should fail"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentId").value("comment_post_002"))
                .andExpect(jsonPath("$.data.content").value("Should fail"));

        mockMvc.perform(delete("/api/comments/comment_post_002")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentId").value("comment_post_002"))
                .andExpect(jsonPath("$.data.isDeleted").value(true));

        mockMvc.perform(get("/api/posts/post_other_secret/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMENT_TARGET_NOT_FOUND"));
    }

    @Test
    void localUploadCreatesMediaAndCanBeAttachedToPost() throws Exception {
        String accessToken = loginAndGetAccessToken();
        byte[] fileBytes = "fake-image-data".getBytes(StandardCharsets.UTF_8);

        MvcResult tokenResult = mockMvc.perform(post("/api/uploads/token")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "upload-demo.jpg",
                                  "mimeType": "image/jpeg",
                                  "fileSizeBytes": 15,
                                  "mediaType": "image",
                                  "width": 800,
                                  "height": 600,
                                  "durationMillis": null,
                                  "displayTimeMillis": 1777416600000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("local"))
                .andExpect(jsonPath("$.data.state").value("waiting"))
                .andReturn();

        String uploadId = readField(tokenResult, "/data/uploadId");
        MockMultipartFile multipartFile = new MockMultipartFile("file", "upload-demo.jpg", "image/jpeg", fileBytes);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/uploads/" + uploadId + "/file")
                        .file(multipartFile)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("success"))
                .andExpect(jsonPath("$.data.media.mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.data.media.sizeBytes").value(15))
                .andReturn();

        String mediaId = readField(uploadResult, "/data/media/mediaId");

        mockMvc.perform(get("/api/media/files/" + mediaId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));

        mockMvc.perform(post("/api/posts/post_003/media")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mediaIds": ["%s"],
                                  "coverMediaId": "%s"
                                }
                                """.formatted(mediaId, mediaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverMediaId").value(mediaId))
                .andExpect(jsonPath("$.data.mediaItems.length()").value(3))
                .andExpect(jsonPath("$.data.mediaItems[2].media.mediaId").value(mediaId));

        mockMvc.perform(get("/api/media/feed")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].mediaId").value(mediaId))
                .andExpect(jsonPath("$.data[0].postIds[0]").value("post_003"));
    }

    @Test
    void directoryDeleteAndSystemDeleteHaveDifferentTrashBehavior() throws Exception {
        String accessToken = loginAndGetAccessToken();

        MvcResult directoryDeleteResult = mockMvc.perform(delete("/api/posts/post_001/media/media_002")
                        .queryParam("deleteMode", "directory")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemType").value("mediaRemoved"))
                .andReturn();

        String mediaRemovedTrashId = readField(directoryDeleteResult, "/data/trashItemId");

        mockMvc.perform(get("/api/posts/post_001")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaCount").value(2))
                .andExpect(jsonPath("$.data.mediaItems[1].media.mediaId").value("media_004"));

        mockMvc.perform(get("/api/trash/items/" + mediaRemovedTrashId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canRestore").value(true))
                .andExpect(jsonPath("$.data.item.itemType").value("mediaRemoved"));

        mockMvc.perform(post("/api/trash/items/" + mediaRemovedTrashId + "/restore")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("restored"));

        mockMvc.perform(get("/api/posts/post_001")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaCount").value(3))
                .andExpect(jsonPath("$.data.mediaItems[1].media.mediaId").value("media_002"));

        MvcResult systemDeleteResult = mockMvc.perform(delete("/api/media/media_001")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemType").value("mediaSystemDeleted"))
                .andReturn();

        String systemTrashId = readField(systemDeleteResult, "/data/trashItemId");

        mockMvc.perform(get("/api/media/feed")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].mediaId").value("media_002"));

        mockMvc.perform(get("/api/posts/post_001")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaCount").value(2))
                .andExpect(jsonPath("$.data.coverMediaId").value("media_002"));

        mockMvc.perform(get("/api/media/media_001/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMENT_TARGET_NOT_FOUND"));

        mockMvc.perform(post("/api/trash/items/" + systemTrashId + "/remove")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trashItemId").value(systemTrashId))
                .andExpect(jsonPath("$.data.undoDeadlineMillis").isNumber());

        mockMvc.perform(get("/api/trash/pending-cleanup")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].trashItemId").value(systemTrashId));

        mockMvc.perform(post("/api/trash/items/" + systemTrashId + "/undo-remove")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("inTrash"));

        mockMvc.perform(post("/api/trash/items/" + systemTrashId + "/restore")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("restored"));

        mockMvc.perform(get("/api/posts/post_001")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaCount").value(3));

        mockMvc.perform(get("/api/media/media_001/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments.length()").value(2));
    }

    @Test
    void deletedPostCanBeRestoredFromTrashWithCommentVisibility() throws Exception {
        String accessToken = loginAndGetAccessToken();

        MvcResult deleteResult = mockMvc.perform(delete("/api/posts/post_002")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemType").value("postDeleted"))
                .andReturn();

        String trashItemId = readField(deleteResult, "/data/trashItemId");

        mockMvc.perform(get("/api/posts/post_002")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));

        mockMvc.perform(get("/api/posts/post_002/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMENT_TARGET_NOT_FOUND"));

        mockMvc.perform(get("/api/albums/album_003/posts")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(post("/api/trash/items/" + trashItemId + "/restore")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("restored"));

        mockMvc.perform(get("/api/posts/post_002")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value("post_002"));

        mockMvc.perform(get("/api/posts/post_002/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments.length()").value(0));
    }

    private String loginAndGetAccessToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account": "demo.a@yingshi.local",
                                  "password": "demo123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return readField(loginResult, "/data/accessToken");
    }

    private String readField(MvcResult mvcResult, String pointer) throws Exception {
        return JsonPath.read(mvcResult.getResponse().getContentAsString(), "$" + pointer.replace("/", "."));
    }

}
