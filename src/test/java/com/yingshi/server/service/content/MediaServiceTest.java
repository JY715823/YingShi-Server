package com.yingshi.server.service.content;

import com.yingshi.server.AbstractPostgresIntegrationTest;
import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.domain.UserEntity;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-layer tests for {@link MediaService#updateMediaTime}.
 * 验证通用"修改媒体显示时间" endpoint 的业务逻辑:
 * - 鉴权 (recordOwnerUserId == userId)
 * - displayTimeMillis / displayTimeSource 字段更新
 * - 媒体不存在 / 软删除抛 404
 * - displayTimeMillis 非正数抛 400
 *
 * 使用 Testcontainers PostgreSQL via {@link AbstractPostgresIntegrationTest}.
 */
class MediaServiceTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MediaService mediaService;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private UserRepository userRepository;

    private static final String LIBRARY_ID = "library_shared";
    private static final String USER_A_ID = "user_demo_a";
    private static final String USER_B_ID = "user_demo_b";

    // ---- Helpers ----

    private AuthenticatedUser buildCurrentUser(String account) {
        UserEntity user = userRepository.findByAccount(account)
                .orElseThrow(() -> new IllegalStateException("Seed user not found: " + account));
        return new AuthenticatedUser(user.getId(), user.getAccount(), user.getDisplayName(),
                user.getDefaultLibraryId(), "test-session");
    }

    private AuthenticatedUser currentUser() {
        return buildCurrentUser(ACCOUNT_A);
    }

    private AuthenticatedUser partnerUser() {
        return buildCurrentUser(ACCOUNT_B);
    }

    private MediaEntity createMedia(String libraryId, String ownerUserId) {
        MediaEntity media = new MediaEntity();
        media.setId(IdGenerator.newId("media"));
        media.setLibraryId(libraryId);
        media.setMediaType(MediaType.IMAGE);
        media.setUrl("https://test.example.com/media/" + media.getId() + ".jpg");
        media.setPreviewUrl("https://test.example.com/media/" + media.getId() + "_preview.jpg");
        media.setMimeType("image/jpeg");
        media.setSizeBytes(1024L);
        media.setWidth(100);
        media.setHeight(100);
        media.setAspectRatio(1.0);
        media.setDisplayTimeMillis(System.currentTimeMillis());
        media.setImportedAtMillis(System.currentTimeMillis());
        media.setDisplayTimeSource("ORIGINAL");
        media.setStoragePath("/test-storage/" + media.getId());
        media.setRecordOwnerUserId(ownerUserId);
        media.setUploadedByUserId(ownerUserId);
        media.setDomain("photo");
        return mediaRepository.save(media);
    }

    // ---- TC-M1: updateMediaTime 成功更新 displayTimeMillis 和 displayTimeSource ----

    @Test
    void updateMediaTimeUpdatesFieldsAndReturnsNewTime() {
        AuthenticatedUser user = currentUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);

        long newDisplayTime = 1_600_000_000_000L; // 2020-09-13
        Long updatedTime = mediaService.updateMediaTime(media.getId(), newDisplayTime, user);

        assertThat(updatedTime).isEqualTo(newDisplayTime);

        // Verify DB persistence
        MediaEntity updated = mediaRepository.findById(media.getId()).orElseThrow();
        assertThat(updated.getDisplayTimeMillis()).isEqualTo(newDisplayTime);
        assertThat(updated.getDisplayTimeSource()).isEqualTo("MANUAL");
    }

    // ---- TC-M2: updateMediaTime 权限校验 - 他人媒体禁止 ----

    @Test
    void updateMediaTimeForbiddenForOtherUser() {
        AuthenticatedUser userA = currentUser();
        AuthenticatedUser userB = partnerUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID); // owner = A

        long newDisplayTime = 1_600_000_000_000L;
        assertThatThrownBy(() -> mediaService.updateMediaTime(media.getId(), newDisplayTime, userB))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("You can only update your own media time");

        // Verify DB NOT updated
        MediaEntity unchanged = mediaRepository.findById(media.getId()).orElseThrow();
        assertThat(unchanged.getDisplayTimeSource()).isEqualTo("ORIGINAL"); // 仍是原值, 未变 MANUAL
    }

    // ---- TC-M3: updateMediaTime 媒体不存在抛 404 ----

    @Test
    void updateMediaTimeNotFoundThrows() {
        AuthenticatedUser user = currentUser();
        long newDisplayTime = 1_600_000_000_000L;

        assertThatThrownBy(() -> mediaService.updateMediaTime("nonexistent_media_id", newDisplayTime, user))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Media was not found");
    }

    // ---- TC-M4: updateMediaTime 软删除媒体抛 404 ----

    @Test
    void updateMediaTimeSoftDeletedThrows() {
        AuthenticatedUser user = currentUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);
        // 模拟软删除
        media.setDeletedAt(java.time.Instant.now());
        mediaRepository.save(media);

        long newDisplayTime = 1_600_000_000_000L;
        assertThatThrownBy(() -> mediaService.updateMediaTime(media.getId(), newDisplayTime, user))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Media was not found");
    }

    // ---- TC-M5: updateMediaTime displayTimeMillis 为 null 抛 400 ----

    @Test
    void updateMediaTimeNullValueThrowsValidationError() {
        AuthenticatedUser user = currentUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);

        assertThatThrownBy(() -> mediaService.updateMediaTime(media.getId(), null, user))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    // ---- TC-M6: updateMediaTime displayTimeMillis 为 0 抛 400 ----

    @Test
    void updateMediaTimeZeroValueThrowsValidationError() {
        AuthenticatedUser user = currentUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);

        assertThatThrownBy(() -> mediaService.updateMediaTime(media.getId(), 0L, user))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    // ---- TC-M7: updateMediaTime displayTimeMillis 为负数抛 400 ----

    @Test
    void updateMediaTimeNegativeValueThrowsValidationError() {
        AuthenticatedUser user = currentUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);

        assertThatThrownBy(() -> mediaService.updateMediaTime(media.getId(), -1L, user))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    // ---- TC-M8: updateMediaTime 后再次更新应覆盖前值 ----

    @Test
    void updateMediaTimeTwiceOverwritesPreviousValue() {
        AuthenticatedUser user = currentUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);

        long firstTime = 1_600_000_000_000L;
        long secondTime = 1_700_000_000_000L;

        mediaService.updateMediaTime(media.getId(), firstTime, user);
        mediaService.updateMediaTime(media.getId(), secondTime, user);

        MediaEntity updated = mediaRepository.findById(media.getId()).orElseThrow();
        assertThat(updated.getDisplayTimeMillis()).isEqualTo(secondTime);
        assertThat(updated.getDisplayTimeSource()).isEqualTo("MANUAL");
    }
}
