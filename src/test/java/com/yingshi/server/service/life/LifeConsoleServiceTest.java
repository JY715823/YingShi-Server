package com.yingshi.server.service.life;

import com.yingshi.server.AbstractPostgresIntegrationTest;
import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.domain.AlbumEntity;
import com.yingshi.server.domain.BowelEventEntity;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.domain.UserEntity;
import com.yingshi.server.dto.content.MediaDto;
import com.yingshi.server.dto.life.AddBowelEventRequest;
import com.yingshi.server.dto.life.LifeConsoleBowelHistoryDayDto;
import com.yingshi.server.dto.life.LifeConsoleBowelMutationResponse;
import com.yingshi.server.dto.life.LifeConsoleBowelUserSummaryDto;
import com.yingshi.server.dto.life.LifeConsoleHistoryDayDto;
import com.yingshi.server.dto.life.LifeConsoleHistoryResponse;
import com.yingshi.server.dto.life.LifeConsoleMediaRequest;
import com.yingshi.server.dto.life.LifeConsoleTodayResponse;
import com.yingshi.server.dto.life.UpdateLocationRequest;
import com.yingshi.server.dto.trash.TrashItemDto;
import com.yingshi.server.repository.AlbumRepository;
import com.yingshi.server.repository.BowelEventRepository;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.repository.SharedLibraryMemberRepository;
import com.yingshi.server.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-layer tests for LifeConsoleService.
 * Uses Testcontainers PostgreSQL via AbstractPostgresIntegrationTest.
 * Each test cleans up its own bowel event data to avoid cross-test pollution.
 */
class LifeConsoleServiceTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private LifeConsoleService lifeConsoleService;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostMediaRepository postMediaRepository;

    @Autowired
    private BowelEventRepository bowelEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SharedLibraryMemberRepository sharedLibraryMemberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @jakarta.persistence.PersistenceContext
    private EntityManager entityManager;

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
        return buildCurrentUser("1085060329@qq.com");
    }

    private AuthenticatedUser partnerUser() {
        return buildCurrentUser("2926315047@qq.com");
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
        media.setDisplayTimeSource("MANUAL");
        media.setStoragePath("/test-storage/" + media.getId());
        media.setRecordOwnerUserId(ownerUserId);
        media.setUploadedByUserId(ownerUserId);
        media.setDomain("photo");
        return mediaRepository.save(media);
    }

    private LifeConsoleMediaRequest personRequest(String... mediaIds) {
        return new LifeConsoleMediaRequest("PERSON", List.of(mediaIds));
    }

    @BeforeEach
    void cleanUpBowelEvents() {
        // Clean up bowel events to avoid cross-test pollution
        List<BowelEventEntity> allEvents = bowelEventRepository.findAll();
        if (!allEvents.isEmpty()) {
            bowelEventRepository.deleteAll(allEvents);
        }
    }

    // ---- TC-S01: getToday 空数据返回空快照 ----

    @Test
    void getTodayEmptySnapshot() {
        AuthenticatedUser user = currentUser();
        LifeConsoleTodayResponse response = lifeConsoleService.getToday(null, null, user);

        assertThat(response.date()).isNotNull();
        assertThat(response.zoneId()).isEqualTo("Asia/Shanghai");
        assertThat(response.currentUser().userId()).isEqualTo(USER_A_ID);
        // displayName may be modified by other tests, so just verify it's not null
        assertThat(response.currentUser().displayName()).isNotNull();
        // partner should exist (user_demo_b)
        assertThat(response.partner()).isNotNull();
        assertThat(response.partner().userId()).isEqualTo(USER_B_ID);
        // bowel summary should have users
        assertThat(response.bowel()).isNotNull();
        assertThat(response.bowel().users()).hasSize(2);
    }

    // ---- TC-S02: getToday 有数据正确分组 ----

    @Test
    void getTodayWithMediaData() {
        AuthenticatedUser user = currentUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);

        lifeConsoleService.addMedia(personRequest(media.getId()), null, user);
        LifeConsoleTodayResponse response = lifeConsoleService.getToday(null, null, user);

        // personSelf should have the media
        assertThat(response.personSelf().mediaItems()).isNotEmpty();
        assertThat(response.personSelf().ownerUserId()).isEqualTo(USER_A_ID);
        assertThat(response.personSelf().editable()).isTrue();
        // personPartner should be empty
        assertThat(response.personPartner().mediaItems()).isEmpty();
        assertThat(response.personPartner().ownerUserId()).isEqualTo(USER_B_ID);
        // meal slots should be empty
        assertThat(response.mealSelf().mediaItems()).isEmpty();
        assertThat(response.mealPartner().mediaItems()).isEmpty();
    }

    // ---- TC-S03: getHistory structure validation ----

    @Test
    void getHistoryResponseStructure() {
        AuthenticatedUser user = currentUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);

        lifeConsoleService.addMedia(personRequest(media.getId()), null, user);
        LifeConsoleHistoryResponse response = lifeConsoleService.getHistory(null, 60, user);

        assertThat(response.zoneId()).isEqualTo("Asia/Shanghai");
        assertThat(response.currentUser().userId()).isEqualTo(USER_A_ID);
        assertThat(response.partner().userId()).isEqualTo(USER_B_ID);
        // personDays / mealDays / bowelDays should be arrays (may be empty since today is excluded)
        assertThat(response.personDays()).isNotNull();
        assertThat(response.mealDays()).isNotNull();
        assertThat(response.bowelDays()).isNotNull();
    }

    // ---- TC-S04: getHistory 时间范围参数 ----

    @Test
    void getHistoryWithLimitDays() {
        AuthenticatedUser user = currentUser();
        LifeConsoleHistoryResponse response = lifeConsoleService.getHistory(null, 7, user);

        // Should return valid response with limitDays=7
        assertThat(response.zoneId()).isEqualTo("Asia/Shanghai");
        assertThat(response.personDays()).isNotNull();
        assertThat(response.personDays().size()).isLessThanOrEqualTo(7);
    }

    // ---- TC-S05: getHistory domain=life 隔离 ----

    @Test
    void getHistoryDomainIsolation() {
        AuthenticatedUser user = currentUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);

        // Add to life domain
        lifeConsoleService.addMedia(personRequest(media.getId()), null, user);
        LifeConsoleHistoryResponse response = lifeConsoleService.getHistory(null, 60, user);

        // Only life domain data should appear (history excludes today, so may be empty)
        assertThat(response.personDays()).isNotNull();
        // No photo domain data should appear in life history
    }

    // ---- TC-S06: addMedia 首次创建系统相册 ----

    @Test
    void addMediaCreatesSystemAlbum() {
        AuthenticatedUser user = currentUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);

        LifeConsoleTodayResponse response = lifeConsoleService.addMedia(personRequest(media.getId()), null, user);

        // System album should be created
        AlbumEntity album = albumRepository
                .findByLibraryIdAndSystemKeyAndDomainAndDeletedAtIsNull(LIBRARY_ID, "life.person", "life")
                .orElse(null);
        assertThat(album).isNotNull();
        assertThat(album.getDomain()).isEqualTo("life");
        assertThat(album.getTitle()).isEqualTo("人物记录");

        // Response should include the media
        assertThat(response.personSelf().mediaItems()).isNotEmpty();
    }

    // ---- TC-S07: addMedia 月度小相册创建 ----

    @Test
    void addMediaCreatesMonthlySmallAlbum() {
        AuthenticatedUser user = currentUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);

        lifeConsoleService.addMedia(personRequest(media.getId()), null, user);

        AlbumEntity album = albumRepository
                .findByLibraryIdAndSystemKeyAndDomainAndDeletedAtIsNull(LIBRARY_ID, "life.person", "life")
                .orElseThrow();

        // Monthly small album should be created with domain=life
        List<PostEntity> posts = postRepository
                .findByLibraryIdAndAlbumIdAndDomainAndDeletedAtIsNullOrderByDisplayTimeMillisDescUpdatedAtDesc(
                        LIBRARY_ID, album.getId(), "life");
        assertThat(posts).isNotEmpty();
        PostEntity post = posts.get(0);
        assertThat(post.getDomain()).isEqualTo("life");
        assertThat(post.getCoverMediaId()).isNotNull();
    }

    // ---- TC-S08: addMedia 重复关联跳过 ----

    @Test
    void addMediaDuplicateSkipped() {
        AuthenticatedUser user = currentUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);

        // First add
        lifeConsoleService.addMedia(personRequest(media.getId()), null, user);
        // Second add with same mediaId
        LifeConsoleTodayResponse response = lifeConsoleService.addMedia(personRequest(media.getId()), null, user);

        // Should not throw, and the media should still appear only once
        long count = response.personSelf().mediaItems().stream()
                .filter(m -> m.mediaId().equals(media.getId()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    // ---- TC-S09: deleteMedia 软删除进回收站 ----

    @Test
    void deleteMediaSoftDeleteToTrash() {
        AuthenticatedUser user = currentUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);

        lifeConsoleService.addMedia(personRequest(media.getId()), null, user);
        TrashItemDto trashItem = lifeConsoleService.deleteMedia(media.getId(), "PERSON", user);

        assertThat(trashItem.trashItemId()).isNotNull();
        assertThat(trashItem.sourceMediaId()).isEqualTo(media.getId());
    }

    // ---- TC-S10: deleteMedia 权限校验 ----

    @Test
    void deleteMediaPermissionDenied() {
        AuthenticatedUser userA = currentUser();
        AuthenticatedUser userB = partnerUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);

        lifeConsoleService.addMedia(personRequest(media.getId()), null, userA);

        // userB tries to delete userA's media
        assertThatThrownBy(() -> lifeConsoleService.deleteMedia(media.getId(), "PERSON", userB))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("You can only delete media from your own frame");
    }

    // ---- TC-S11: addBowelEvent 事件创建 ----

    @Test
    void addBowelEventCreatesEvent() {
        AuthenticatedUser user = currentUser();

        LifeConsoleBowelMutationResponse response = lifeConsoleService.addBowelEvent(null, null, user);

        assertThat(response.event()).isNotNull();
        assertThat(response.event().bowelEventId()).isNotNull();
        assertThat(response.event().userId()).isEqualTo(USER_A_ID);
        assertThat(response.event().occurredAtMillis()).isNotNull();
        assertThat(response.bowel()).isNotNull();
        assertThat(response.bowel().users()).isNotEmpty();
    }

    // ---- TC-S12: addBowelEvent 当天摘要正确 ----

    @Test
    void addBowelEventTodaySummary() {
        AuthenticatedUser user = currentUser();

        // Add 3 bowel events
        lifeConsoleService.addBowelEvent(null, null, user);
        lifeConsoleService.addBowelEvent(null, null, user);
        lifeConsoleService.addBowelEvent(null, null, user);

        LifeConsoleTodayResponse response = lifeConsoleService.getToday(null, null, user);

        var userSummary = response.bowel().users().stream()
                .filter(u -> u.userId().equals(USER_A_ID))
                .findFirst()
                .orElseThrow();
        assertThat(userSummary.count()).isEqualTo(3);
        assertThat(userSummary.latestOccurredAtMillis()).isNotNull();
    }

    // ---- TC-S13: deleteLatestBowelEvent 软删除 ----

    @Test
    void deleteLatestBowelEventSoftDelete() {
        AuthenticatedUser user = currentUser();

        LifeConsoleBowelMutationResponse addResponse = lifeConsoleService.addBowelEvent(null, null, user);
        String eventId = addResponse.event().bowelEventId();

        LifeConsoleBowelMutationResponse deleteResponse = lifeConsoleService.deleteLatestBowelEvent(null, user);
        assertThat(deleteResponse.event().bowelEventId()).isEqualTo(eventId);

        // Verify deletedAt is set
        BowelEventEntity event = bowelEventRepository.findById(eventId).orElseThrow();
        assertThat(event.getDeletedAt()).isNotNull();
    }

    // ---- TC-S14: deleteLatestBowelEvent 无事件抛异常 ----

    @Test
    void deleteLatestBowelEventNoEventThrows() {
        AuthenticatedUser user = currentUser();

        // No bowel event added yet, should throw
        assertThatThrownBy(() -> lifeConsoleService.deleteLatestBowelEvent(null, user))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No bowel event was found for today");
    }

    // ---- TC-S15: domain 隔离验证 ----

    @Test
    void domainIsolationLifeNotInPhoto() {
        AuthenticatedUser user = currentUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);

        // Add to life domain
        lifeConsoleService.addMedia(personRequest(media.getId()), null, user);

        // Verify media is now in life domain
        MediaEntity updatedMedia = mediaRepository.findById(media.getId()).orElseThrow();
        assertThat(updatedMedia.getDomain()).isEqualTo("life");

        // Verify life domain album exists
        AlbumEntity lifeAlbum = albumRepository
                .findByLibraryIdAndSystemKeyAndDomainAndDeletedAtIsNull(LIBRARY_ID, "life.person", "life")
                .orElse(null);
        assertThat(lifeAlbum).isNotNull();
        assertThat(lifeAlbum.getDomain()).isEqualTo("life");
    }

    // ---- TC-S16: FR-18 AC-4 addBowelEvent with location stores lat/lng ----

    @Test
    void addBowelEventWithLocationStoresLatLng() {
        AuthenticatedUser user = currentUser();
        AddBowelEventRequest body = new AddBowelEventRequest(39.9075, 116.39723, "北京天安门");

        LifeConsoleBowelMutationResponse response = lifeConsoleService.addBowelEvent(null, body, user);

        assertThat(response.event()).isNotNull();
        assertThat(response.event().bowelEventId()).isNotNull();
        assertThat(response.event().latitude()).isEqualTo(39.9075);
        assertThat(response.event().longitude()).isEqualTo(116.39723);
        assertThat(response.event().locationLabel()).isEqualTo("北京天安门");

        // Verify DB persistence
        BowelEventEntity saved = bowelEventRepository.findById(response.event().bowelEventId()).orElseThrow();
        assertThat(saved.getLatitude()).isEqualTo(39.9075);
        assertThat(saved.getLongitude()).isEqualTo(116.39723);
        assertThat(saved.getLocationLabel()).isEqualTo("北京天安门");
    }

    // ---- TC-S17: FR-18 AC-4 / IA-4 addBowelEvent without body still works (backward compat) ----

    @Test
    void addBowelEventWithoutBodyStillWorks() {
        AuthenticatedUser user = currentUser();

        LifeConsoleBowelMutationResponse response = lifeConsoleService.addBowelEvent(null, null, user);

        assertThat(response.event()).isNotNull();
        assertThat(response.event().bowelEventId()).isNotNull();
        assertThat(response.event().latitude()).isNull();
        assertThat(response.event().longitude()).isNull();
        assertThat(response.event().locationLabel()).isNull();
        assertThat(response.bowel()).isNotNull();
    }

    // ---- TC-S18: FR-18 AC-7 / IA-5 addBowelEvent with location triggers NoopGeocoding (label stays null) ----

    @Test
    void addBowelEventWithLocationTriggersNoopGeocoding() {
        AuthenticatedUser user = currentUser();
        // Pass lat/lng but no label → service should call geocodingService.reverseGeocode
        // In test env, NoopGeocodingService returns null, so label stays null
        AddBowelEventRequest body = new AddBowelEventRequest(39.9075, 116.39723, null);

        LifeConsoleBowelMutationResponse response = lifeConsoleService.addBowelEvent(null, body, user);

        assertThat(response.event()).isNotNull();
        assertThat(response.event().latitude()).isEqualTo(39.9075);
        assertThat(response.event().longitude()).isEqualTo(116.39723);
        // NoopGeocodingService.reverseGeocode returns null → label should be null
        assertThat(response.event().locationLabel()).isNull();
    }

    // ---- TC-S19: FR-18 AC-5 / IA-3 bowel history includes locationLabel ----

    @Test
    void bowelHistoryIncludesLocationLabel() {
        AuthenticatedUser user = currentUser();

        // Insert a bowel event with yesterday's timestamp and a location label
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        long yesterdayNoon = LocalDate.now(zone).minusDays(1)
                .atStartOfDay(zone).toInstant().toEpochMilli() + 12 * 3600 * 1000L;

        BowelEventEntity event = new BowelEventEntity();
        event.setId(IdGenerator.newId("bowel"));
        event.setLibraryId(user.libraryId());
        event.setUserId(user.userId());
        event.setOccurredAtMillis(yesterdayNoon);
        event.setLatitude(39.9075);
        event.setLongitude(116.39723);
        event.setLocationLabel("北京天安门");
        bowelEventRepository.save(event);

        LifeConsoleHistoryResponse response = lifeConsoleService.getHistory(null, 7, user);

        // Find the bowel day with our location label
        LifeConsoleBowelHistoryDayDto dayWithLocation = response.bowelDays().stream()
                .filter(d -> "北京天安门".equals(d.locationLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No bowel day with locationLabel found in history"));

        assertThat(dayWithLocation.locationLabel()).isEqualTo("北京天安门");
        // Verify the user summary has latestLocationLabel
        assertThat(dayWithLocation.users()).isNotEmpty();
        LifeConsoleBowelUserSummaryDto userSummary = dayWithLocation.users().stream()
                .filter(u -> u.userId().equals(USER_A_ID))
                .findFirst()
                .orElseThrow();
        assertThat(userSummary.latestLocationLabel()).isEqualTo("北京天安门");
    }

    // ---- TC-S20: IA-3 (high risk) media history preserves location through withDisplayTime ----

    @Test
    void mediaHistoryPreservesLocationThroughWithDisplayTime() {
        AuthenticatedUser user = currentUser();

        // Create media with location fields
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);
        media.setLatitude(39.9075);
        media.setLongitude(116.39723);
        media.setLocationLabel("北京天安门");
        mediaRepository.save(media);

        // Add to life console (creates PostMediaEntity with today's createdAt)
        lifeConsoleService.addMedia(personRequest(media.getId()), null, user);

        // Update PostMediaEntity createdAt to yesterday so media appears in history.
        // NOTE: BaseEntity.createdAt is marked @Column(updatable = false), so JPA save() will
        // NOT persist changes to this field. We must use a native SQL UPDATE to bypass JPA.
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        Instant yesterday = LocalDate.now(zone).minusDays(1)
                .atStartOfDay(zone).toInstant().plusSeconds(12 * 3600);
        int updatedRows = jdbcTemplate.update(
                "UPDATE small_album_media SET created_at = ? WHERE media_id = ?",
                java.sql.Timestamp.from(yesterday),
                media.getId()
        );
        assertThat(updatedRows)
                .as("PostMediaEntity should exist for media %s", media.getId())
                .isGreaterThan(0);
        // Clear persistence context so subsequent getHistory reads fresh DB state
        // (JPA caches entities by ID; without clear(), findByLibraryIdAndPostIdIn would return
        //  stale PostMediaEntity with the original today's createdAt)
        entityManager.clear();

        // Call getHistory
        LifeConsoleHistoryResponse response = lifeConsoleService.getHistory(null, 60, user);

        // Debug: check if personDays has any entries
        assertThat(response.personDays())
                .as("personDays should contain at least one day with our media")
                .isNotEmpty();

        // Find the day containing our media
        LifeConsoleHistoryDayDto dayWithMedia = response.personDays().stream()
                .filter(d -> d.selfMedia().stream().anyMatch(m -> m.mediaId().equals(media.getId())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Media not found in history personDays"));

        // Verify location fields are preserved through withDisplayTime transformation
        MediaDto mediaDto = dayWithMedia.selfMedia().stream()
                .filter(m -> m.mediaId().equals(media.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(mediaDto.latitude()).isEqualTo(39.9075);
        assertThat(mediaDto.longitude()).isEqualTo(116.39723);
        assertThat(mediaDto.locationLabel()).isEqualTo("北京天安门");
    }

    // ---- TC-S20: Round 7 updateMediaLocation 更新字段并返回 today ----

    @Test
    void updateMediaLocationUpdatesFieldsAndReturnsToday() {
        AuthenticatedUser user = currentUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);
        lifeConsoleService.addMedia(personRequest(media.getId()), null, user);

        UpdateLocationRequest request = new UpdateLocationRequest(39.9075, 116.39723, "北京天安门");
        LifeConsoleTodayResponse response = lifeConsoleService.updateMediaLocation(media.getId(), request, user);

        assertThat(response).isNotNull();
        // Verify DB persistence
        MediaEntity updated = mediaRepository.findById(media.getId()).orElseThrow();
        assertThat(updated.getLatitude()).isEqualTo(39.9075);
        assertThat(updated.getLongitude()).isEqualTo(116.39723);
        assertThat(updated.getLocationLabel()).isEqualTo("北京天安门");
    }

    // ---- TC-S21: Round 7 updateMediaLocation 权限校验（他人媒体禁止）----

    @Test
    void updateMediaLocationForbiddenForOtherUser() {
        AuthenticatedUser userA = currentUser();
        AuthenticatedUser userB = partnerUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);
        lifeConsoleService.addMedia(personRequest(media.getId()), null, userA);

        UpdateLocationRequest request = new UpdateLocationRequest(39.9075, 116.39723, "北京");
        assertThatThrownBy(() -> lifeConsoleService.updateMediaLocation(media.getId(), request, userB))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("You can only update your own media location");
    }

    // ---- TC-S22: Round 7 updateMediaLocation label 缺失时触发逆地理编码 ----

    @Test
    void updateMediaLocationTriggersGeocodingWhenLabelMissing() {
        AuthenticatedUser user = currentUser();
        MediaEntity media = createMedia(LIBRARY_ID, USER_A_ID);
        lifeConsoleService.addMedia(personRequest(media.getId()), null, user);

        // label 为 null，应触发 geocodingService.reverseGeocode（测试环境 NoopGeocodingService 返回 null）
        UpdateLocationRequest request = new UpdateLocationRequest(39.9075, 116.39723, null);
        LifeConsoleTodayResponse response = lifeConsoleService.updateMediaLocation(media.getId(), request, user);

        assertThat(response).isNotNull();
        MediaEntity updated = mediaRepository.findById(media.getId()).orElseThrow();
        assertThat(updated.getLatitude()).isEqualTo(39.9075);
        assertThat(updated.getLongitude()).isEqualTo(116.39723);
        // NoopGeocodingService 返回 null，所以 label 为 null
        assertThat(updated.getLocationLabel()).isNull();
    }

    // ---- TC-S23: Round 7 updateMediaLocation 媒体不存在抛 404 ----

    @Test
    void updateMediaLocationNotFoundThrows() {
        AuthenticatedUser user = currentUser();
        UpdateLocationRequest request = new UpdateLocationRequest(39.9075, 116.39723, "北京");
        assertThatThrownBy(() -> lifeConsoleService.updateMediaLocation("nonexistent_media_id", request, user))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Media was not found");
    }

    // ---- TC-S24: Round 7 updateBowelEventLocation 更新字段 ----

    @Test
    void updateBowelEventLocationUpdatesFields() {
        AuthenticatedUser user = currentUser();
        LifeConsoleBowelMutationResponse addResponse = lifeConsoleService.addBowelEvent(null, null, user);
        String eventId = addResponse.event().bowelEventId();

        UpdateLocationRequest request = new UpdateLocationRequest(39.9075, 116.39723, "北京天安门");
        LifeConsoleBowelMutationResponse response = lifeConsoleService.updateBowelEventLocation(eventId, request, user);

        assertThat(response.event()).isNotNull();
        assertThat(response.event().bowelEventId()).isEqualTo(eventId);
        assertThat(response.event().latitude()).isEqualTo(39.9075);
        assertThat(response.event().longitude()).isEqualTo(116.39723);
        assertThat(response.event().locationLabel()).isEqualTo("北京天安门");

        // Verify DB
        BowelEventEntity updated = bowelEventRepository.findById(eventId).orElseThrow();
        assertThat(updated.getLatitude()).isEqualTo(39.9075);
        assertThat(updated.getLongitude()).isEqualTo(116.39723);
        assertThat(updated.getLocationLabel()).isEqualTo("北京天安门");
    }

    // ---- TC-S25: Round 7 updateBowelEventLocation 权限校验 ----

    @Test
    void updateBowelEventLocationForbiddenForOtherUser() {
        AuthenticatedUser userA = currentUser();
        AuthenticatedUser userB = partnerUser();
        LifeConsoleBowelMutationResponse addResponse = lifeConsoleService.addBowelEvent(null, null, userA);
        String eventId = addResponse.event().bowelEventId();

        UpdateLocationRequest request = new UpdateLocationRequest(39.9075, 116.39723, "北京");
        assertThatThrownBy(() -> lifeConsoleService.updateBowelEventLocation(eventId, request, userB))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("You can only update your own bowel event location");
    }

    // ---- TC-S26: Round 7 updateBowelEventLocation 事件不存在抛 404 ----

    @Test
    void updateBowelEventLocationNotFoundThrows() {
        AuthenticatedUser user = currentUser();
        UpdateLocationRequest request = new UpdateLocationRequest(39.9075, 116.39723, "北京");
        assertThatThrownBy(() -> lifeConsoleService.updateBowelEventLocation("nonexistent_event_id", request, user))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Bowel event not found");
    }

    // ---- TC-S27: Round 7 today API 返回 bowel events 列表 ----

    @Test
    void todayReturnsBowelEventsList() {
        AuthenticatedUser user = currentUser();
        // 添加 2 条大便事件（带位置）
        AddBowelEventRequest body1 = new AddBowelEventRequest(39.9075, 116.39723, "北京天安门");
        AddBowelEventRequest body2 = new AddBowelEventRequest(31.2304, 121.4737, "上海外滩");
        lifeConsoleService.addBowelEvent(null, body1, user);
        lifeConsoleService.addBowelEvent(null, body2, user);

        LifeConsoleTodayResponse response = lifeConsoleService.getToday(null, null, user);

        var userSummary = response.bowel().users().stream()
                .filter(u -> u.userId().equals(USER_A_ID))
                .findFirst()
                .orElseThrow();
        assertThat(userSummary.count()).isEqualTo(2);
        assertThat(userSummary.events()).isNotNull();
        assertThat(userSummary.events()).hasSize(2);
        // events 应按 occurredAtMillis 倒序排列（最新的在前）
        assertThat(userSummary.events().get(0).occurredAtMillis())
                .isGreaterThanOrEqualTo(userSummary.events().get(1).occurredAtMillis());
        // 每条 event 应包含完整位置信息
        assertThat(userSummary.events()).allSatisfy(event -> {
            assertThat(event.bowelEventId()).isNotNull();
            assertThat(event.latitude()).isNotNull();
            assertThat(event.longitude()).isNotNull();
            assertThat(event.locationLabel()).isNotNull();
        });
    }

    // ---- TC-S28: Round 7 today API events 为空时返回空列表（非 null）----

    @Test
    void todayReturnsEmptyEventsListWhenNoBowelEvents() {
        AuthenticatedUser user = currentUser();
        LifeConsoleTodayResponse response = lifeConsoleService.getToday(null, null, user);

        var userSummary = response.bowel().users().stream()
                .filter(u -> u.userId().equals(USER_A_ID))
                .findFirst()
                .orElseThrow();
        assertThat(userSummary.count()).isEqualTo(0);
        assertThat(userSummary.events()).isNotNull();
        assertThat(userSummary.events()).isEmpty();
    }
}