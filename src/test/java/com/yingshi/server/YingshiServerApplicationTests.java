package com.yingshi.server;

import com.jayway.jsonpath.JsonPath;
import com.yingshi.server.domain.PushDeviceTokenEntity;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.TrashItemRepository;
import com.yingshi.server.service.push.PushDeliveryResult;
import com.yingshi.server.service.push.PushMessageSender;
import com.yingshi.server.service.trash.PendingCleanupScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "yingshi.dev.test-import.enabled=false",
        "yingshi.dev.recovery.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:yingshi-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class YingshiServerApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private PendingCleanupScheduler pendingCleanupScheduler;

    @Autowired
    private TrashItemRepository trashItemRepository;

    @Autowired
    private CapturingPushMessageSender capturingPushMessageSender;

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
                .andExpect(jsonPath("$.data.libraryId").value("library_shared"))
                .andExpect(jsonPath("$.data.libraryDisplayName").value("我们的小空间"))
                .andExpect(jsonPath("$.data.partner.userId").value("user_demo_b"))
                .andExpect(jsonPath("$.data.partner.account").value("demo.b@yingshi.local"))
                .andExpect(jsonPath("$.data.partner.displayName").value("另一半"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        String accessToken = readField(loginResult, "/data/accessToken");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("user_demo_a"))
                .andExpect(jsonPath("$.data.account").value("demo.a@yingshi.local"))
                .andExpect(jsonPath("$.data.libraryId").value("library_shared"))
                .andExpect(jsonPath("$.data.libraryDisplayName").value("我们的小空间"))
                .andExpect(jsonPath("$.data.partner.userId").value("user_demo_b"))
                .andExpect(jsonPath("$.data.partner.account").value("demo.b@yingshi.local"))
                .andExpect(jsonPath("$.data.partner.displayName").value("另一半"));

        String demoBAccessToken = loginAndGetAccessToken("demo.b@yingshi.local", "demo123456");
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + demoBAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("user_demo_b"))
                .andExpect(jsonPath("$.data.account").value("demo.b@yingshi.local"))
                .andExpect(jsonPath("$.data.libraryId").value("library_shared"))
                .andExpect(jsonPath("$.data.libraryDisplayName").value("我们的小空间"))
                .andExpect(jsonPath("$.data.partner.userId").value("user_demo_a"))
                .andExpect(jsonPath("$.data.partner.account").value("demo.a@yingshi.local"))
                .andExpect(jsonPath("$.data.partner.displayName").value("映世小屋"));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));
    }

    @Test
    void ledgerSnapshotCanRoundTripAcrossSharedLibrary() throws Exception {
        String demoAAccessToken = loginAndGetAccessToken("demo.a@yingshi.local", "demo123456");
        String demoBAccessToken = loginAndGetAccessToken("demo.b@yingshi.local", "demo123456");

        mockMvc.perform(get("/api/ledger/snapshot")
                        .header("Authorization", "Bearer " + demoAAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionMillis").value(0))
                .andExpect(jsonPath("$.data.payload").doesNotExist());

        mockMvc.perform(put("/api/ledger/snapshot")
                        .header("Authorization", "Bearer " + demoAAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payload": {
                                    "books": [
                                      {
                                        "id": "book-001",
                                        "name": "共享账本",
                                        "template": "daily",
                                        "currencyCode": "CNY",
                                        "currencySymbol": "¥",
                                        "coverColor": 4283215696,
                                        "sortOrder": 0,
                                        "createdAtMillis": 1780000000000,
                                        "updatedAtMillis": 1780000000000,
                                        "isDeleted": false
                                      }
                                    ],
                                    "categories": [],
                                    "accounts": [],
                                    "transactions": [],
                                    "budgets": [],
                                    "categoryBudgets": [],
                                    "deletedItems": [],
                                    "recurringRules": [],
                                    "recurringOccurrences": []
                                }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionMillis").isNumber())
                .andExpect(jsonPath("$.data.payload.books[0].id").value("book-001"))
                .andExpect(jsonPath("$.data.payload.books[0].name").value("共享账本"));

        mockMvc.perform(get("/api/ledger/snapshot")
                        .header("Authorization", "Bearer " + demoBAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionMillis").isNumber())
                .andExpect(jsonPath("$.data.payload.books[0].id").value("book-001"))
                .andExpect(jsonPath("$.data.payload.books[0].name").value("共享账本"));
    }

    @Test
    void chatSnapshotCanRoundTripAcrossSharedLibrary() throws Exception {
        String demoAAccessToken = loginAndGetAccessToken("demo.a@yingshi.local", "demo123456");
        String demoBAccessToken = loginAndGetAccessToken("demo.b@yingshi.local", "demo123456");

        mockMvc.perform(get("/api/chat/imported/snapshot")
                        .header("Authorization", "Bearer " + demoAAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionMillis").value(0))
                .andExpect(jsonPath("$.data.payload").doesNotExist());

        mockMvc.perform(put("/api/chat/imported/snapshot")
                        .header("Authorization", "Bearer " + demoAAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payload": {
                                    "sessions": [
                                      {
                                        "chatId": "chat-import-001",
                                        "title": "June trip chat",
                                        "subtitle": "Hangzhou plan",
                                        "coverPreset": "sunset",
                                        "importedAtMillis": 1780000000000,
                                        "updatedAtMillis": 1780000005000,
                                        "messageCount": 12,
                                        "unreadCount": 1,
                                        "lastMessagePreview": "Add the West Lake night walk too",
                                        "participants": [
                                          {
                                            "id": "user-a",
                                            "displayName": "A"
                                          },
                                          {
                                            "id": "user-b",
                                            "displayName": "B"
                                          }
                                        ],
                                        "messages": [
                                          {
                                            "id": "msg-001",
                                            "senderId": "user-a",
                                            "senderName": "A",
                                            "text": "Add the West Lake night walk too",
                                            "timestampMillis": 1780000004000,
                                            "isDeleted": false
                                          }
                                        ]
                                      }
                                    ]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionMillis").isNumber())
                .andExpect(jsonPath("$.data.payload.sessions[0].chatId").value("chat-import-001"))
                .andExpect(jsonPath("$.data.payload.sessions[0].title").value("June trip chat"))
                .andExpect(jsonPath("$.data.payload.sessions[0].messages[0].text").value("Add the West Lake night walk too"));

        mockMvc.perform(get("/api/chat/imported/snapshot")
                        .header("Authorization", "Bearer " + demoBAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionMillis").isNumber())
                .andExpect(jsonPath("$.data.payload.sessions[0].chatId").value("chat-import-001"))
                .andExpect(jsonPath("$.data.payload.sessions[0].title").value("June trip chat"))
                .andExpect(jsonPath("$.data.payload.sessions[0].messages[0].text").value("Add the West Lake night walk too"));
    }

    @Test
    void logoutRevokesCurrentAccessToken() throws Exception {
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

        String accessToken = readField(loginResult, "/data/accessToken");

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_INVALID"));
    }

    @Test
    void refreshRotationInvalidatesOldRefreshTokenAndSupportsLogoutBody() throws Exception {
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

        String accessToken = readField(loginResult, "/data/accessToken");
        String refreshToken = readField(loginResult, "/data/refreshToken");

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        String rotatedAccessToken = readField(refreshResult, "/data/accessToken");
        String rotatedRefreshToken = readField(refreshResult, "/data/refreshToken");

        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_INVALID"));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + rotatedAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(rotatedRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_INVALID"));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + rotatedAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_INVALID"));
    }

    @Test
    void currentUserProfileCanBeReadAndUpdatedByOwner() throws Exception {
        String demoAAccessToken = loginAndGetAccessToken("demo.a@yingshi.local", "demo123456");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + demoAAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("映世小屋"))
                .andExpect(jsonPath("$.data.bio").value("一起把平常日子慢慢收进这座小小相册。"))
                .andExpect(jsonPath("$.data.partner.displayName").value("另一半"))
                .andExpect(jsonPath("$.data.createdAtMillis").isNumber())
                .andExpect(jsonPath("$.data.updatedAtMillis").isNumber());

        mockMvc.perform(patch("/api/auth/me/profile")
                        .header("Authorization", "Bearer " + demoAAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "映世小屋",
                                  "bio": "把两个人的日常安静收进这里。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("映世小屋"))
                .andExpect(jsonPath("$.data.bio").value("把两个人的日常安静收进这里。"))
                .andExpect(jsonPath("$.data.partner.displayName").value("另一半"));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + demoAAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("映世小屋"))
                .andExpect(jsonPath("$.data.bio").value("把两个人的日常安静收进这里。"))
                .andExpect(jsonPath("$.data.partner.displayName").value("另一半"));

        String demoBAccessToken = loginAndGetAccessToken("demo.b@yingshi.local", "demo123456");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + demoBAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("另一半"))
                .andExpect(jsonPath("$.data.bio").value("把生活里的闪光片段，也把安静和想念一起留下来。"))
                .andExpect(jsonPath("$.data.partner.displayName").value("映世小屋"));
    }
    @Test
    void protectedEndpointRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));

        mockMvc.perform(patch("/api/auth/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Nope",
                                  "bio": "Nope"
                                }
                                """))
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

        mockMvc.perform(get("/api/albums/album_001/small-albums")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].smallAlbumId").value("post_001"));

        mockMvc.perform(get("/api/small-albums/post_001")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smallAlbumId").value("post_001"))
                .andExpect(jsonPath("$.data.coverMediaId").value("media_001"))
                .andExpect(jsonPath("$.data.mediaItems.length()").value(3))
                .andExpect(jsonPath("$.data.mediaItems[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.mediaItems[0].media.mediaId").value("media_001"))
                .andExpect(jsonPath("$.data.mediaItems[0].media.url").value("/api/media/files/media_001"))
                .andExpect(jsonPath("$.data.mediaItems[0].media.previewUrl").value("/api/media/files/media_001?variant=preview"))
                .andExpect(jsonPath("$.data.mediaItems[2].media.mediaType").value("image"));

        mockMvc.perform(get("/api/media/feed")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").doesNotExist())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[0].mediaId").value("media_001"))
                .andExpect(jsonPath("$.data[0].url").value("/api/media/files/media_001"))
                .andExpect(jsonPath("$.data[0].smallAlbumIds.length()").value(2));

        MvcResult firstFeedPageResult = mockMvc.perform(get("/api/media/feed")
                        .queryParam("pageSize", "2")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].mediaId").value("media_001"))
                .andExpect(jsonPath("$.data[1].mediaId").value("media_002"))
                .andExpect(jsonPath("$.page.pageSize").value(2))
                .andExpect(jsonPath("$.page.hasMore").value(true))
                .andExpect(jsonPath("$.page.nextCursor").isNotEmpty())
                .andReturn();

        String nextCursor = readField(firstFeedPageResult, "/page/nextCursor");
        mockMvc.perform(get("/api/media/feed")
                        .queryParam("cursor", nextCursor)
                        .queryParam("pageSize", "2")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].mediaId").value("media_003"))
                .andExpect(jsonPath("$.data[1].mediaId").value("media_004"))
                .andExpect(jsonPath("$.page.hasMore").value(true));

        mockMvc.perform(get("/api/media/files/media_001")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Accept-Ranges", "bytes"));

        mockMvc.perform(get("/api/media/files/media_006")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Range", "bytes=0-99"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Range", startsWith("bytes 0-99/")))
                .andExpect(header().longValue("Content-Length", 100L));
    }

    @Test
    void contentMutationApisWorkForCurrentSpace() throws Exception {
        String accessToken = loginAndGetAccessToken();

        MvcResult createResult = mockMvc.perform(post("/api/small-albums")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Fresh Layout",
                                  "summary": "Built from seeded media",
                                  "contributorLabel": "Demo A",
                                  "displayTimeMillis": 1777413000000,
                                  "albumId": "album_003",
                                  "initialMediaIds": ["media_003", "media_005"],
                                  "coverMediaId": "media_005"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Fresh Layout"))
                .andExpect(jsonPath("$.data.albumId").value("album_003"))
                .andExpect(jsonPath("$.data.coverMediaId").value("media_005"))
                .andReturn();

        String postId = readField(createResult, "/data/smallAlbumId");

        mockMvc.perform(post("/api/small-albums")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Empty Draft",
                                  "summary": "Created before media is attached",
                                  "contributorLabel": "Demo A",
                                  "displayTimeMillis": 1777413100000,
                                  "albumId": "album_001",
                                  "initialMediaIds": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Empty Draft"))
                .andExpect(jsonPath("$.data.coverMediaId").doesNotExist())
                .andExpect(jsonPath("$.data.mediaCount").value(0))
                .andExpect(jsonPath("$.data.mediaItems.length()").value(0));

        mockMvc.perform(patch("/api/small-albums/" + postId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Fresh Layout Updated",
                                  "summary": "Updated summary",
                                  "albumId": "album_002"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Fresh Layout Updated"))
                .andExpect(jsonPath("$.data.albumId").value("album_002"));

        mockMvc.perform(patch("/api/small-albums/" + postId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Fresh Layout Stable",
                                  "summary": "Updated summary again",
                                  "albumId": "album_002"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Fresh Layout Stable"))
                .andExpect(jsonPath("$.data.albumId").value("album_002"));

        mockMvc.perform(patch("/api/small-albums/" + postId + "/cover")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "coverMediaId": "media_003"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverMediaId").value("media_003"));

        mockMvc.perform(patch("/api/small-albums/" + postId + "/media-order")
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

        mockMvc.perform(get("/api/small-albums/post_other_secret")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SMALL_ALBUM_NOT_FOUND"));
    }

    @Test
    void commentApisWorkAndPostMediaFlowsStaySeparated() throws Exception {
        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(get("/api/small-albums/post_001/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments.length()").value(2))
                .andExpect(jsonPath("$.data.comments[0].targetType").value("SMALL_ALBUM"))
                .andExpect(jsonPath("$.data.comments[0].smallAlbumId").value("post_001"))
                .andExpect(jsonPath("$.data.comments[0].mediaId").value(nullValue()));

        mockMvc.perform(get("/api/media/media_001/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments.length()").value(2))
                .andExpect(jsonPath("$.data.comments[0].targetType").value("MEDIA"))
                .andExpect(jsonPath("$.data.comments[0].mediaId").value("media_001"))
                .andExpect(jsonPath("$.data.comments[0].smallAlbumId").value(nullValue()));

        MvcResult postCommentResult = mockMvc.perform(post("/api/small-albums/post_001/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "New post comment"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetType").value("SMALL_ALBUM"))
                .andExpect(jsonPath("$.data.smallAlbumId").value("post_001"))
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
                .andExpect(jsonPath("$.data.smallAlbumId").value(nullValue()))
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
    void sameLibraryMemberCanEditOtherCommentAndOtherLibraryStillBlocked() throws Exception {
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

        mockMvc.perform(get("/api/small-albums/post_other_secret/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMENT_TARGET_NOT_FOUND"));
    }

    @Test
    void notificationsIncludeCommentCreateEditDeleteVariants() throws Exception {
        String demoAAccessToken = loginAndGetAccessToken("demo.a@yingshi.local", "demo123456");
        String demoBAccessToken = loginAndGetAccessToken("demo.b@yingshi.local", "demo123456");

        MvcResult createdCommentResult = mockMvc.perform(post("/api/small-albums/post_001/comments")
                        .header("Authorization", "Bearer " + demoBAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "B created comment for notifications"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String createdCommentId = readField(createdCommentResult, "/data/commentId");

        MvcResult authorCommentResult = mockMvc.perform(post("/api/media/media_001/comments")
                        .header("Authorization", "Bearer " + demoAAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "A original comment for edit/delete notifications"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String authorCommentId = readField(authorCommentResult, "/data/commentId");

        mockMvc.perform(patch("/api/comments/" + authorCommentId)
                        .header("Authorization", "Bearer " + demoBAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "B edited A's comment"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("B edited A's comment"));

        mockMvc.perform(delete("/api/comments/" + authorCommentId)
                        .header("Authorization", "Bearer " + demoBAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDeleted").value(true));

        MvcResult notificationsResult = mockMvc.perform(get("/api/notifications")
                        .queryParam("limit", "20")
                        .header("Authorization", "Bearer " + demoAAccessToken))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> notifications = JsonPath.parse(
                        notificationsResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .read("$.data");

        assertTrue(notifications.stream().anyMatch(notification ->
                ("comment:" + createdCommentId).equals(notification.get("notificationId"))
                        && "comment".equals(notification.get("type"))
        ));
        assertTrue(notifications.stream().anyMatch(notification ->
                String.valueOf(notification.get("notificationId")).startsWith("comment-edit:" + authorCommentId + ":")
                        && "comment_edit".equals(notification.get("type"))
                        && "media_001".equals(notification.get("mediaId"))
        ));
        assertTrue(notifications.stream().anyMatch(notification ->
                String.valueOf(notification.get("notificationId")).startsWith("comment-delete:" + authorCommentId + ":")
                        && "comment_delete".equals(notification.get("type"))
        ));
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
                                  "displayTimeMillis": 1777416600000,
                                  "sourceFingerprint": "test-upload-source-001"
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
        MediaEntity uploadedMedia = mediaRepository.findById(mediaId).orElseThrow();
        assertEquals("local", uploadedMedia.getStorageProvider());
        assertEquals("yingshi-media", uploadedMedia.getBucket());
        assertEquals("originals/2026/04/" + mediaId + ".jpg", uploadedMedia.getOriginalObjectKey());
        assertEquals(uploadedMedia.getStoragePath(), uploadedMedia.getOriginalObjectKey());
        assertNotNull(uploadedMedia.getChecksum());
        assertFalse(uploadedMedia.getOriginalObjectKey().contains("://"));

        mockMvc.perform(get("/api/media/files/" + mediaId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));

        mockMvc.perform(post("/api/small-albums/post_003/media")
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

        MvcResult feedResult = mockMvc.perform(get("/api/media/feed")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> matchedItems = JsonPath.parse(feedResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .read("$.data[?(@.mediaId=='%s')]".formatted(mediaId));
        assertEquals(1, matchedItems.size());
        @SuppressWarnings("unchecked")
        List<String> smallAlbumIds = (List<String>) matchedItems.get(0).get("smallAlbumIds");
        assertTrue(smallAlbumIds.contains("post_003"));

        MvcResult duplicateTokenResult = mockMvc.perform(post("/api/uploads/token")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "upload-demo-copy.jpg",
                                  "mimeType": "image/jpeg",
                                  "fileSizeBytes": 999,
                                  "mediaType": "image",
                                  "width": 800,
                                  "height": 600,
                                  "durationMillis": null,
                                  "displayTimeMillis": 1777416600000,
                                  "sourceFingerprint": "test-upload-source-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String duplicateUploadId = readField(duplicateTokenResult, "/data/uploadId");
        MockMultipartFile duplicateMultipartFile = new MockMultipartFile("file", "upload-demo-copy.jpg", "application/octet-stream", fileBytes);

        mockMvc.perform(multipart("/api/uploads/" + duplicateUploadId + "/file")
                        .file(duplicateMultipartFile)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.media.mediaId").value(mediaId));

        MvcResult duplicateFeedResult = mockMvc.perform(get("/api/media/feed")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> duplicateMatchedItems = JsonPath.parse(duplicateFeedResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .read("$.data[?(@.mediaId=='%s')]".formatted(mediaId));
        assertEquals(1, duplicateMatchedItems.size());
    }

    @Test
    void lifeConsoleArchivesMediaAndEnforcesOwnershipRules() throws Exception {
        String demoAAccessToken = loginAndGetAccessToken("demo.a@yingshi.local", "demo123456");
        String demoBAccessToken = loginAndGetAccessToken("demo.b@yingshi.local", "demo123456");
        String personMediaId = uploadTestMedia(
                demoAAccessToken,
                "life-person.jpg",
                "life-console-person-" + System.nanoTime()
        );
        String mealMediaId = uploadTestMedia(
                demoAAccessToken,
                "life-meal.jpg",
                "life-console-meal-" + System.nanoTime()
        );

        mockMvc.perform(post("/api/life-console/media")
                        .header("Authorization", "Bearer " + demoAAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "PERSON",
                                  "mediaIds": ["%s"]
                                }
                                """.formatted(personMediaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personSelf.editable").value(true))
                .andExpect(jsonPath("$.data.personSelf.mediaItems.length()").value(1))
                .andExpect(jsonPath("$.data.personSelf.mediaItems[0].mediaId").value(personMediaId))
                .andExpect(jsonPath("$.data.personSelf.mediaItems[0].recordOwnerUserId").value("user_demo_a"))
                .andExpect(jsonPath("$.data.personPartner.editable").value(false));

        mockMvc.perform(post("/api/life-console/media")
                        .header("Authorization", "Bearer " + demoAAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "MEAL",
                                  "mediaIds": ["%s"]
                                }
                                """.formatted(mealMediaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mealSelf.editable").value(true))
                .andExpect(jsonPath("$.data.mealSelf.mediaItems.length()").value(1))
                .andExpect(jsonPath("$.data.mealSelf.mediaItems[0].mediaId").value(mealMediaId))
                .andExpect(jsonPath("$.data.mealSelf.mediaItems[0].recordOwnerUserId").value("user_demo_a"))
                .andExpect(jsonPath("$.data.mealPartner.editable").value(false));

        mockMvc.perform(get("/api/life-console/today")
                        .header("Authorization", "Bearer " + demoBAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personSelf.mediaItems.length()").value(0))
                .andExpect(jsonPath("$.data.personPartner.mediaItems.length()").value(1))
                .andExpect(jsonPath("$.data.personPartner.mediaItems[0].mediaId").value(personMediaId))
                .andExpect(jsonPath("$.data.personPartner.editable").value(false))
                .andExpect(jsonPath("$.data.mealPartner.mediaItems[0].mediaId").value(mealMediaId));

        MvcResult albumsResult = mockMvc.perform(get("/api/albums")
                        .header("Authorization", "Bearer " + demoAAccessToken))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> personAlbums = readFilteredList(albumsResult, "$.data[?(@.systemKey=='life.person')]");
        List<Map<String, Object>> mealAlbums = readFilteredList(albumsResult, "$.data[?(@.systemKey=='life.meal')]");
        assertEquals(1, personAlbums.size());
        assertEquals(1, mealAlbums.size());
        assertEquals(Boolean.TRUE, personAlbums.get(0).get("includeInPhotoFeed"));
        assertEquals(Boolean.FALSE, mealAlbums.get(0).get("includeInPhotoFeed"));

        YearMonth currentMonth = YearMonth.now(ZoneId.of("Asia/Shanghai"));
        String expectedMonthTitle = "%04d年%02d月".formatted(currentMonth.getYear(), currentMonth.getMonthValue());
        String personAlbumId = (String) personAlbums.get(0).get("albumId");
        String mealAlbumId = (String) mealAlbums.get(0).get("albumId");
        MvcResult personSmallAlbumsResult = mockMvc.perform(get("/api/albums/" + personAlbumId + "/small-albums")
                        .header("Authorization", "Bearer " + demoAAccessToken))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult mealSmallAlbumsResult = mockMvc.perform(get("/api/albums/" + mealAlbumId + "/small-albums")
                        .header("Authorization", "Bearer " + demoAAccessToken))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> personSmallAlbums = readFilteredList(personSmallAlbumsResult, "$.data[?(@.systemKey=='%s')]".formatted(currentMonth));
        List<Map<String, Object>> mealSmallAlbums = readFilteredList(mealSmallAlbumsResult, "$.data[?(@.systemKey=='%s')]".formatted(currentMonth));
        assertEquals(1, personSmallAlbums.size());
        assertEquals(1, mealSmallAlbums.size());
        assertEquals(expectedMonthTitle, personSmallAlbums.get(0).get("title"));
        assertEquals(expectedMonthTitle, mealSmallAlbums.get(0).get("title"));

        MvcResult feedResult = mockMvc.perform(get("/api/media/feed")
                        .header("Authorization", "Bearer " + demoAAccessToken))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> personFeedItems = readFilteredList(feedResult, "$.data[?(@.mediaId=='%s')]".formatted(personMediaId));
        List<Map<String, Object>> mealFeedItems = readFilteredList(feedResult, "$.data[?(@.mediaId=='%s')]".formatted(mealMediaId));
        assertEquals(1, personFeedItems.size());
        assertEquals(0, mealFeedItems.size());

        mockMvc.perform(delete("/api/life-console/media/" + personMediaId)
                        .queryParam("category", "PERSON")
                        .header("Authorization", "Bearer " + demoBAccessToken))
                .andExpect(status().isForbidden());

        MvcResult deleteResult = mockMvc.perform(delete("/api/life-console/media/" + personMediaId)
                        .queryParam("category", "PERSON")
                        .header("Authorization", "Bearer " + demoAAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemType").value("mediaSystemDeleted"))
                .andExpect(jsonPath("$.data.sourceMediaId").value(personMediaId))
                .andReturn();
        assertNotNull(readField(deleteResult, "/data/trashItemId"));

        mockMvc.perform(get("/api/life-console/today")
                        .header("Authorization", "Bearer " + demoAAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personSelf.mediaItems.length()").value(0))
                .andExpect(jsonPath("$.data.mealSelf.mediaItems.length()").value(1));
    }

    @Test
    void lifeConsoleBowelEventsAreVisibleToBothUsersButOnlyMutateCurrentUser() throws Exception {
        String demoAAccessToken = loginAndGetAccessToken("demo.a@yingshi.local", "demo123456");
        String demoBAccessToken = loginAndGetAccessToken("demo.b@yingshi.local", "demo123456");

        mockMvc.perform(post("/api/life-console/bowel-events")
                        .header("Authorization", "Bearer " + demoAAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bowel.users[0].count").value(1));
        mockMvc.perform(post("/api/life-console/bowel-events")
                        .header("Authorization", "Bearer " + demoBAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bowel.users[0].count").value(1));

        MvcResult todayForA = mockMvc.perform(get("/api/life-console/today")
                        .header("Authorization", "Bearer " + demoAAccessToken))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(1, readBowelCount(todayForA, "user_demo_a"));
        assertEquals(1, readBowelCount(todayForA, "user_demo_b"));

        MvcResult deletedForA = mockMvc.perform(delete("/api/life-console/bowel-events/latest")
                        .header("Authorization", "Bearer " + demoAAccessToken))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(0, readBowelCount(deletedForA, "user_demo_a"));
        assertEquals(1, readBowelCount(deletedForA, "user_demo_b"));
    }

    @Test
    void pushDeviceTokenRegistrationAndLifeConsoleChangePushWork() throws Exception {
        String demoAAccessToken = loginAndGetAccessToken("demo.a@yingshi.local", "demo123456");
        String demoBAccessToken = loginAndGetAccessToken("demo.b@yingshi.local", "demo123456");
        capturingPushMessageSender.clear();

        mockMvc.perform(post("/api/push/device-tokens")
                        .header("Authorization", "Bearer " + demoAAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platform": "android",
                                  "token": "fcm-token-a"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platform").value("android"))
                .andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(post("/api/push/device-tokens")
                        .header("Authorization", "Bearer " + demoBAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platform": "android",
                                  "token": "fcm-token-b"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/life-console/bowel-events")
                        .header("Authorization", "Bearer " + demoAAccessToken))
                .andExpect(status().isOk());

        assertEquals(1, capturingPushMessageSender.deliveries.size());
        CapturedPushDelivery delivery = capturingPushMessageSender.deliveries.get(0);
        assertEquals(List.of("fcm-token-b"), delivery.tokens);
        assertEquals("life_console.changed", delivery.data.get("type"));
        assertEquals("life_console.changed", delivery.data.get("event"));
        assertEquals("user_demo_a", delivery.data.get("actorUserId"));
        assertEquals("bowel_added", delivery.data.get("reason"));
    }

    @Test
    void directoryDeleteAndSystemDeleteHaveDifferentTrashBehavior() throws Exception {
        String accessToken = loginAndGetAccessToken();

        MvcResult directoryDeleteResult = mockMvc.perform(delete("/api/small-albums/post_001/media/media_002")
                        .queryParam("deleteMode", "directory")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemType").value("mediaRemoved"))
                .andExpect(jsonPath("$.data.sourceMediaId").value("media_002"))
                .andExpect(jsonPath("$.data.commentTargetMediaId").value("media_002"))
                .andReturn();

        String mediaRemovedTrashId = readField(directoryDeleteResult, "/data/trashItemId");

        mockMvc.perform(get("/api/small-albums/post_001")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaCount").value(2))
                .andExpect(jsonPath("$.data.mediaItems[1].media.mediaId").value("media_004"));

        mockMvc.perform(get("/api/trash/items/" + mediaRemovedTrashId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canRestore").value(true))
                .andExpect(jsonPath("$.data.item.itemType").value("mediaRemoved"))
                .andExpect(jsonPath("$.data.item.commentTargetMediaId").value("media_002"));

        mockMvc.perform(post("/api/trash/items/" + mediaRemovedTrashId + "/restore")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("restored"));

        mockMvc.perform(get("/api/small-albums/post_001")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaCount").value(3))
                .andExpect(jsonPath("$.data.mediaItems[1].media.mediaId").value("media_002"));

        mockMvc.perform(post("/api/media/media_001/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "trash viewer real chain comment"
                                }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaId").value("media_001"));

        MvcResult systemDeleteResult = mockMvc.perform(delete("/api/media/media_001")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemType").value("mediaSystemDeleted"))
                .andExpect(jsonPath("$.data.sourceMediaId").value("media_001"))
                .andExpect(jsonPath("$.data.commentTargetMediaId").value("media_001"))
                .andReturn();

        String systemTrashId = readField(systemDeleteResult, "/data/trashItemId");

        mockMvc.perform(get("/api/media/feed")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].mediaId").value("media_002"));

        mockMvc.perform(get("/api/small-albums/post_001")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaCount").value(2))
                .andExpect(jsonPath("$.data.coverMediaId").value("media_002"));

        mockMvc.perform(get("/api/trash/items/" + systemTrashId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.itemType").value("mediaSystemDeleted"))
                .andExpect(jsonPath("$.data.item.sourceMediaId").value("media_001"))
                .andExpect(jsonPath("$.data.item.commentTargetMediaId").value("media_001"));

        mockMvc.perform(get("/api/media/media_001/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments.length()").value(3))
                .andExpect(jsonPath("$.data.comments[0].content").value("trash viewer real chain comment"));

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

        mockMvc.perform(get("/api/small-albums/post_001")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaCount").value(3));

        mockMvc.perform(get("/api/media/media_001/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments.length()").value(3));
    }

    @Test
    void directoryDeleteCanLeaveSmallAlbumEmpty() throws Exception {
        String accessToken = loginAndGetAccessToken();

        MvcResult createResult = mockMvc.perform(post("/api/small-albums")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Single Memory",
                                  "summary": "Only one media inside",
                                  "contributorLabel": "Demo A",
                                  "displayTimeMillis": 1777418800000,
                                  "albumId": "album_001",
                                  "initialMediaIds": ["media_005"],
                                  "coverMediaId": "media_005"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaCount").value(1))
                .andReturn();

        String postId = readField(createResult, "/data/smallAlbumId");

        mockMvc.perform(delete("/api/small-albums/" + postId + "/media/media_005")
                        .queryParam("deleteMode", "directory")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemType").value("mediaRemoved"))
                .andExpect(jsonPath("$.data.sourceSmallAlbumId").value(postId))
                .andExpect(jsonPath("$.data.sourceMediaId").value("media_005"));

        mockMvc.perform(get("/api/small-albums/" + postId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaCount").value(0))
                .andExpect(jsonPath("$.data.mediaItems.length()").value(0));
    }

    @Test
    void emptySmallAlbumCanStillBeDeletedToTrash() throws Exception {
        String accessToken = loginAndGetAccessToken();

        MvcResult createResult = mockMvc.perform(post("/api/small-albums")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Disposable Memory",
                                  "summary": "Create, empty, then delete",
                                  "contributorLabel": "Demo A",
                                  "displayTimeMillis": 1777419800000,
                                  "albumId": "album_001",
                                  "initialMediaIds": ["media_004"],
                                  "coverMediaId": "media_004"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaCount").value(1))
                .andReturn();

        String postId = readField(createResult, "/data/smallAlbumId");

        mockMvc.perform(delete("/api/small-albums/" + postId + "/media/media_004")
                        .queryParam("deleteMode", "directory")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemType").value("mediaRemoved"));

        MvcResult deleteResult = mockMvc.perform(delete("/api/small-albums/" + postId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemType").value("smallAlbumDeleted"))
                .andExpect(jsonPath("$.data.sourceSmallAlbumId").value(postId))
                .andReturn();

        String trashItemId = readField(deleteResult, "/data/trashItemId");

        mockMvc.perform(get("/api/small-albums/" + postId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SMALL_ALBUM_NOT_FOUND"));

        mockMvc.perform(get("/api/trash/items/" + trashItemId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.itemType").value("smallAlbumDeleted"))
                .andExpect(jsonPath("$.data.item.relatedMediaIds.length()").value(0));
    }

    @Test
    void permanentDeleteSystemDeletedMediaRemovesRecordAndLocalFiles() throws Exception {
        String accessToken = loginAndGetAccessToken();
        byte[] fileBytes = jpegBytes();

        MvcResult tokenResult = mockMvc.perform(post("/api/uploads/token")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "purge-demo.jpg",
                                  "mimeType": "image/jpeg",
                                  "fileSizeBytes": %d,
                                  "mediaType": "image",
                                  "width": 800,
                                  "height": 600,
                                  "durationMillis": null,
                                  "displayTimeMillis": 1777416600000,
                                  "sourceFingerprint": "test-purge-source-001"
                                }
                                """.formatted(fileBytes.length)))
                .andExpect(status().isOk())
                .andReturn();

        String uploadId = readField(tokenResult, "/data/uploadId");
        MockMultipartFile multipartFile = new MockMultipartFile("file", "purge-demo.jpg", "image/jpeg", fileBytes);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/uploads/" + uploadId + "/file")
                        .file(multipartFile)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        String mediaId = readField(uploadResult, "/data/media/mediaId");
        Path originalPath = Path.of("local-storage", "originals", "2026", "04", mediaId + ".jpg");
        assertTrue(Files.exists(originalPath), "uploaded original should exist before purge");
        MediaEntity mediaBeforePreview = mediaRepository.findById(mediaId).orElseThrow();
        assertEquals("local", mediaBeforePreview.getStorageProvider());
        assertEquals("yingshi-media", mediaBeforePreview.getBucket());
        assertEquals("originals/2026/04/" + mediaId + ".jpg", mediaBeforePreview.getOriginalObjectKey());
        assertNotNull(mediaBeforePreview.getChecksum());

        MvcResult deleteResult = mockMvc.perform(delete("/api/media/" + mediaId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemType").value("mediaSystemDeleted"))
                .andExpect(jsonPath("$.data.commentTargetMediaId").value(mediaId))
                .andReturn();
        String trashItemId = readField(deleteResult, "/data/trashItemId");

        mockMvc.perform(get("/api/media/files/" + mediaId)
                        .queryParam("variant", "preview")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        MediaEntity mediaAfterPreview = mediaRepository.findById(mediaId).orElseThrow();
        assertEquals("previews/2026/04/" + mediaId + "-preview-v2-1280.jpg", mediaAfterPreview.getPreviewObjectKey());
        assertFalse(mediaAfterPreview.getPreviewObjectKey().contains("://"));
        Path previewPath = Path.of("local-storage", "previews", "2026", "04", mediaId + "-preview-v2-1280.jpg");
        assertTrue(Files.exists(previewPath), "generated preview should exist before purge");

        mockMvc.perform(post("/api/trash/items/" + trashItemId + "/purge")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trashItemId").value(trashItemId));

        mockMvc.perform(get("/api/trash/items/" + trashItemId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRASH_ITEM_NOT_FOUND"));

        mockMvc.perform(get("/api/media/files/" + mediaId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/media/" + mediaId + "/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMENT_TARGET_NOT_FOUND"));
        assertTrue(Files.notExists(originalPath), "original should be physically deleted");
        assertTrue(Files.notExists(previewPath), "preview should be physically deleted");
    }

    @Test
    void deletedPostCanBeRestoredFromTrashWithCommentVisibility() throws Exception {
        String accessToken = loginAndGetAccessToken();

        MvcResult deleteResult = mockMvc.perform(delete("/api/small-albums/post_002")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemType").value("smallAlbumDeleted"))
                .andReturn();

        String trashItemId = readField(deleteResult, "/data/trashItemId");

        mockMvc.perform(get("/api/small-albums/post_002")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SMALL_ALBUM_NOT_FOUND"));

        mockMvc.perform(get("/api/small-albums/post_002/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments.length()").value(0));

        mockMvc.perform(get("/api/albums/album_003/small-albums")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(post("/api/trash/items/" + trashItemId + "/restore")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("restored"));

        mockMvc.perform(get("/api/small-albums/post_002")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smallAlbumId").value("post_002"));

        mockMvc.perform(get("/api/small-albums/post_002/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments.length()").value(0));
    }

    @Test
    void pendingCleanupSchedulerPurgesExpiredItems() throws Exception {
        String accessToken = loginAndGetAccessToken();

        MvcResult deleteResult = mockMvc.perform(delete("/api/media/media_001")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        String trashItemId = readField(deleteResult, "/data/trashItemId");

        mockMvc.perform(post("/api/trash/items/" + trashItemId + "/remove")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trashItemId").value(trashItemId));

        var trashItem = trashItemRepository.findById(trashItemId).orElseThrow();
        trashItem.setUndoDeadlineAt(Instant.now().minusSeconds(5));
        trashItemRepository.save(trashItem);

        pendingCleanupScheduler.purgeExpiredPendingCleanupItems();

        mockMvc.perform(get("/api/trash/items/" + trashItemId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRASH_ITEM_NOT_FOUND"));

        mockMvc.perform(get("/api/media/files/media_001")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    private String loginAndGetAccessToken() throws Exception {
        return loginAndGetAccessToken("demo.a@yingshi.local", "demo123456");
    }

    private String loginAndGetAccessToken(String account, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account": "%s",
                                  "password": "%s"
                                }
                                """.formatted(account, password)))
                .andExpect(status().isOk())
                .andReturn();
        return readField(loginResult, "/data/accessToken");
    }

    private String uploadTestMedia(String accessToken, String fileName, String sourceFingerprint) throws Exception {
        byte[] fileBytes = jpegBytes();
        MvcResult tokenResult = mockMvc.perform(post("/api/uploads/token")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "%s",
                                  "mimeType": "image/jpeg",
                                  "fileSizeBytes": %d,
                                  "mediaType": "image",
                                  "width": 4,
                                  "height": 3,
                                  "durationMillis": null,
                                  "displayTimeMillis": %d,
                                  "sourceFingerprint": "%s"
                                }
                                """.formatted(fileName, fileBytes.length, System.currentTimeMillis(), sourceFingerprint)))
                .andExpect(status().isOk())
                .andReturn();
        String uploadId = readField(tokenResult, "/data/uploadId");
        MockMultipartFile multipartFile = new MockMultipartFile("file", fileName, "image/jpeg", fileBytes);
        MvcResult uploadResult = mockMvc.perform(multipart("/api/uploads/" + uploadId + "/file")
                        .file(multipartFile)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return readField(uploadResult, "/data/media/mediaId");
    }

    private List<Map<String, Object>> readFilteredList(MvcResult mvcResult, String jsonPath) throws Exception {
        return JsonPath.parse(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).read(jsonPath);
    }

    private int readBowelCount(MvcResult mvcResult, String userId) throws Exception {
        List<Integer> counts = JsonPath.parse(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .read("$.data.bowel.users[?(@.userId=='%s')].count".formatted(userId));
        return counts.isEmpty() ? 0 : counts.get(0);
    }

    private byte[] jpegBytes() throws Exception {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", outputStream);
        return outputStream.toByteArray();
    }

    private String readField(MvcResult mvcResult, String pointer) throws Exception {
        return JsonPath.read(mvcResult.getResponse().getContentAsString(), "$" + pointer.replace("/", "."));
    }

    @TestConfiguration
    static class PushTestConfig {
        @Bean
        @Primary
        CapturingPushMessageSender capturingPushMessageSender() {
            return new CapturingPushMessageSender();
        }
    }

    static class CapturingPushMessageSender implements PushMessageSender {
        private final List<CapturedPushDelivery> deliveries = new ArrayList<>();

        @Override
        public PushDeliveryResult sendDataMessage(List<PushDeviceTokenEntity> targetTokens, Map<String, String> data) {
            deliveries.add(new CapturedPushDelivery(
                    targetTokens.stream().map(PushDeviceTokenEntity::getToken).toList(),
                    Map.copyOf(data)
            ));
            return new PushDeliveryResult(targetTokens.size(), targetTokens.size(), List.of());
        }

        void clear() {
            deliveries.clear();
        }
    }

    record CapturedPushDelivery(
            List<String> tokens,
            Map<String, String> data
    ) {
    }

}

