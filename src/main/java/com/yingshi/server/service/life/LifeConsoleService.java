package com.yingshi.server.service.life;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.AlbumEntity;
import com.yingshi.server.domain.BowelEventEntity;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.domain.SharedLibraryMemberEntity;
import com.yingshi.server.domain.UserEntity;
import com.yingshi.server.dto.content.MediaDto;
import com.yingshi.server.dto.life.LifeConsoleBowelEventDto;
import com.yingshi.server.dto.life.LifeConsoleBowelHistoryDayDto;
import com.yingshi.server.dto.life.LifeConsoleBowelMutationResponse;
import com.yingshi.server.dto.life.LifeConsoleBowelSummaryDto;
import com.yingshi.server.dto.life.LifeConsoleBowelUserSummaryDto;
import com.yingshi.server.dto.life.LifeConsoleHistoryDayDto;
import com.yingshi.server.dto.life.LifeConsoleHistoryResponse;
import com.yingshi.server.dto.life.LifeConsoleMediaRequest;
import com.yingshi.server.dto.life.LifeConsoleMediaSlotDto;
import com.yingshi.server.dto.life.LifeConsoleTodayResponse;
import com.yingshi.server.dto.life.LifeConsoleUserDto;
import com.yingshi.server.dto.life.UpdateLocationRequest;
import com.yingshi.server.mapper.ContentMapper;
import com.yingshi.server.repository.AlbumRepository;
import com.yingshi.server.repository.BowelEventRepository;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.repository.SharedLibraryMemberRepository;
import com.yingshi.server.repository.UserRepository;
import com.yingshi.server.service.geocoding.GeocodingService;
import com.yingshi.server.service.push.PushNotificationService;
import com.yingshi.server.service.push.PushDispatchSupport;
import com.yingshi.server.service.trash.TrashService;
import com.yingshi.server.dto.trash.TrashItemDto;
import com.yingshi.server.dto.life.AddBowelEventRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LifeConsoleService {

    private static final String DEFAULT_ZONE_ID = "Asia/Shanghai";

    private final AlbumRepository albumRepository;
    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final MediaRepository mediaRepository;
    private final BowelEventRepository bowelEventRepository;
    private final UserRepository userRepository;
    private final SharedLibraryMemberRepository sharedLibraryMemberRepository;
    private final ContentMapper contentMapper;
    private final TrashService trashService;
    private final PushNotificationService pushNotificationService;
    private final GeocodingService geocodingService;

    public LifeConsoleService(
            AlbumRepository albumRepository,
            PostRepository postRepository,
            PostMediaRepository postMediaRepository,
            MediaRepository mediaRepository,
            BowelEventRepository bowelEventRepository,
            UserRepository userRepository,
            SharedLibraryMemberRepository sharedLibraryMemberRepository,
            ContentMapper contentMapper,
            TrashService trashService,
            PushNotificationService pushNotificationService,
            GeocodingService geocodingService
    ) {
        this.albumRepository = albumRepository;
        this.postRepository = postRepository;
        this.postMediaRepository = postMediaRepository;
        this.mediaRepository = mediaRepository;
        this.bowelEventRepository = bowelEventRepository;
        this.userRepository = userRepository;
        this.sharedLibraryMemberRepository = sharedLibraryMemberRepository;
        this.contentMapper = contentMapper;
        this.trashService = trashService;
        this.pushNotificationService = pushNotificationService;
        this.geocodingService = geocodingService;
    }

    @Transactional(readOnly = true)
    public LifeConsoleTodayResponse getToday(String date, String zoneId, AuthenticatedUser currentUser) {
        ZoneId resolvedZone = parseZoneId(zoneId);
        LocalDate resolvedDate = parseDate(date, resolvedZone);
        DateRange dateRange = dateRange(resolvedDate, resolvedZone);
        LifeUsers users = resolveLifeUsers(currentUser);

        return buildTodayResponse(resolvedDate, resolvedZone, dateRange, users, currentUser);
    }

    @Transactional(readOnly = true)
    public LifeConsoleHistoryResponse getHistory(String zoneId, Integer limitDays, AuthenticatedUser currentUser) {
        ZoneId resolvedZone = parseZoneId(zoneId);
        int safeLimitDays = Math.max(7, Math.min(limitDays == null ? 60 : limitDays, 365));
        LifeUsers users = resolveLifeUsers(currentUser);

        return new LifeConsoleHistoryResponse(
                resolvedZone.getId(),
                toUserDto(users.currentUser()),
                users.partner() == null ? null : toUserDto(users.partner()),
                buildHistoryDays(LifeConsoleCategory.PERSON, users, currentUser.libraryId(), resolvedZone, safeLimitDays),
                buildHistoryDays(LifeConsoleCategory.MEAL, users, currentUser.libraryId(), resolvedZone, safeLimitDays),
                buildBowelHistoryDays(users, currentUser.libraryId(), resolvedZone, safeLimitDays)
        );
    }

    @Transactional
    public LifeConsoleTodayResponse addMedia(LifeConsoleMediaRequest request, String zoneId, AuthenticatedUser currentUser) {
        LifeConsoleCategory category = LifeConsoleCategory.parse(request.category());
        List<String> mediaIds = normalizedDistinctMediaIds(request.mediaIds());
        String libraryId = currentUser.libraryId();
        List<MediaEntity> mediaItems = mediaRepository.findByLibraryIdAndIdInAndDeletedAtIsNull(libraryId, mediaIds);
        if (mediaItems.size() != mediaIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.MEDIA_NOT_FOUND, "One or more mediaIds do not exist in the shared library.");
        }
        Map<String, MediaEntity> mediaById = mediaItems.stream().collect(Collectors.toMap(MediaEntity::getId, Function.identity()));

        ZoneId zone = parseZoneId(zoneId);
        LocalDate today = LocalDate.now(zone);
        AlbumEntity album = ensureSystemAlbum(libraryId, category);

        // Round 8 第十五轮: 按每张照片的 displayTimeMillis 分组到对应月份的 small_album.
        // 用户需求: 今天上传一张昨天/上个月拍的照片, 应该归到对应月份的历史记录, 而不是今天.
        // 之前: 全部归到 LocalDate.now(zone) 的当月 small_album, 用 relation.createdAt 过滤当日 slot.
        // 现在: 按 media 的 displayTimeMillis (优先) / capturedAtMillis / importedAtMillis 计算归属月份.
        Map<YearMonth, List<String>> mediaIdsByMonth = new LinkedHashMap<>();
        for (String mediaId : mediaIds) {
            MediaEntity media = mediaById.get(mediaId);
            YearMonth month = resolveMediaYearMonth(media, zone, today);
            mediaIdsByMonth.computeIfAbsent(month, k -> new ArrayList<>()).add(mediaId);
        }

        for (Map.Entry<YearMonth, List<String>> entry : mediaIdsByMonth.entrySet()) {
            YearMonth month = entry.getKey();
            List<String> monthMediaIds = entry.getValue();
            PostEntity monthlySmallAlbum = ensureMonthlySmallAlbum(libraryId, album, month, zone);
            List<PostMediaEntity> existingRelations = postMediaRepository.findByLibraryIdAndPostIdOrderBySortOrderAsc(
                    libraryId,
                    monthlySmallAlbum.getId()
            );
            Set<String> existingMediaIds = existingRelations.stream()
                    .map(PostMediaEntity::getMediaId)
                    .collect(Collectors.toCollection(HashSet::new));
            int nextSortOrder = existingRelations.stream()
                    .map(PostMediaEntity::getSortOrder)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(0) + 1;

            List<PostMediaEntity> newRelations = new ArrayList<>();
            for (String mediaId : monthMediaIds) {
                MediaEntity media = mediaById.get(mediaId);
                media.setRecordOwnerUserId(currentUser.userId());
                if (media.getUploadedByUserId() == null || media.getUploadedByUserId().isBlank()) {
                    media.setUploadedByUserId(currentUser.userId());
                }
                media.setDomain("life");
                if (!existingMediaIds.contains(mediaId)) {
                    PostMediaEntity relation = new PostMediaEntity();
                    relation.setId(IdGenerator.newId("small_album_media"));
                    relation.setLibraryId(libraryId);
                    relation.setPostId(monthlySmallAlbum.getId());
                    relation.setMediaId(mediaId);
                    relation.setSortOrder(nextSortOrder++);
                    newRelations.add(relation);
                }
            }
            List<MediaEntity> monthMediaEntities = monthMediaIds.stream().map(mediaById::get).toList();
            mediaRepository.saveAll(monthMediaEntities);
            if (!newRelations.isEmpty()) {
                postMediaRepository.saveAll(newRelations);
                postMediaRepository.flush();
            }

            if (monthlySmallAlbum.getCoverMediaId() == null && !monthMediaIds.isEmpty()) {
                monthlySmallAlbum.setCoverMediaId(monthMediaIds.get(0));
                postRepository.save(monthlySmallAlbum);
            }
        }

        if (album.getCoverMediaId() == null && !mediaIds.isEmpty()) {
            album.setCoverMediaId(mediaIds.get(0));
            albumRepository.save(album);
        }

        notifyLifeConsoleChanged(libraryId, currentUser.userId(),
                category.name().toLowerCase() + "_media_added");
        return getToday(today.toString(), zone.getId(), currentUser);
    }

    /**
     * Round 8 第十五轮: 解析媒体归属的年月.
     * 优先用 displayTimeMillis (用户偏好时间), 其次 capturedAtMillis (拍摄时间),
     * 再次 importedAtMillis (导入时间), 最后兜底用当前日期.
     */
    private YearMonth resolveMediaYearMonth(MediaEntity media, ZoneId zone, LocalDate fallbackToday) {
        Long timeMillis = media.getDisplayTimeMillis();
        if (timeMillis == null || timeMillis <= 0L) {
            timeMillis = media.getCapturedAtMillis();
        }
        if (timeMillis == null || timeMillis <= 0L) {
            timeMillis = media.getImportedAtMillis();
        }
        if (timeMillis == null || timeMillis <= 0L) {
            return YearMonth.from(fallbackToday);
        }
        return YearMonth.from(Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDate());
    }

    /**
     * Round 8 第十五轮: 解析媒体的展示时间 (毫秒).
     * 优先级: displayTimeMillis > capturedAtMillis > importedAtMillis > relation.createdAt > 当前时间.
     */
    private long resolveMediaDisplayTime(MediaEntity media, PostMediaEntity relation) {
        if (media.getDisplayTimeMillis() != null && media.getDisplayTimeMillis() > 0L) {
            return media.getDisplayTimeMillis();
        }
        if (media.getCapturedAtMillis() != null && media.getCapturedAtMillis() > 0L) {
            return media.getCapturedAtMillis();
        }
        if (media.getImportedAtMillis() != null && media.getImportedAtMillis() > 0L) {
            return media.getImportedAtMillis();
        }
        if (relation != null && relation.getCreatedAt() != null) {
            return relation.getCreatedAt().toEpochMilli();
        }
        return System.currentTimeMillis();
    }

    @Transactional
    public TrashItemDto deleteMedia(String mediaId, String rawCategory, AuthenticatedUser currentUser) {
        LifeConsoleCategory category = LifeConsoleCategory.parse(rawCategory);
        MediaEntity media = mediaRepository.findByIdAndLibraryIdAndDeletedAtIsNull(mediaId, currentUser.libraryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Media was not found."));
        if (!currentUser.userId().equals(media.getRecordOwnerUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "You can only delete media from your own frame.");
        }
        TrashItemDto trashItem = trashService.systemDeleteMedia(mediaId, currentUser);
        notifyLifeConsoleChanged(currentUser.libraryId(), currentUser.userId(),
                category.name().toLowerCase() + "_media_deleted");
        return trashItem;
    }

    @Transactional
    public LifeConsoleBowelMutationResponse addBowelEvent(String zoneId, AddBowelEventRequest body, AuthenticatedUser currentUser) {
        long nowMillis = Instant.now().toEpochMilli();
        BowelEventEntity event = new BowelEventEntity();
        event.setId(IdGenerator.newId("bowel"));
        event.setLibraryId(currentUser.libraryId());
        event.setUserId(currentUser.userId());
        event.setOccurredAtMillis(nowMillis);
        // FR-18: populate location fields, fall back to server-side reverse geocoding when label is missing
        Double lat = body == null ? null : body.latitude();
        Double lng = body == null ? null : body.longitude();
        String label = body == null ? null : body.locationLabel();
        if (lat != null && lng != null && (label == null || label.isBlank())) {
            label = geocodingService.reverseGeocode(lat, lng);
        }
        event.setLatitude(lat);
        event.setLongitude(lng);
        event.setLocationLabel(label);
        bowelEventRepository.save(event);
        notifyLifeConsoleChanged(currentUser.libraryId(), currentUser.userId(), "bowel_added");

        ZoneId zone = parseZoneId(zoneId);
        LocalDate today = LocalDate.now(zone);
        LifeUsers users = resolveLifeUsers(currentUser);
        return new LifeConsoleBowelMutationResponse(
                toBowelEventDto(event),
                buildBowelSummary(dateRange(today, zone), users, currentUser.libraryId())
        );
    }

    @Transactional
    public LifeConsoleBowelMutationResponse deleteLatestBowelEvent(String zoneId, AuthenticatedUser currentUser) {
        ZoneId zone = parseZoneId(zoneId);
        LocalDate today = LocalDate.now(zone);
        DateRange range = dateRange(today, zone);
        BowelEventEntity event = bowelEventRepository
                .findLatestByLibraryIdAndUserIdAndOccurredAtMillisGreaterThanEqualAndOccurredAtMillisLessThan(
                        currentUser.libraryId(),
                        currentUser.userId(),
                        range.startMillis(),
                        range.endMillis()
                )
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "No bowel event was found for today."));
        LifeConsoleBowelEventDto eventDto = toBowelEventDto(event);
        event.setDeletedAt(Instant.now().toEpochMilli());
        bowelEventRepository.save(event);
        notifyLifeConsoleChanged(currentUser.libraryId(), currentUser.userId(), "bowel_deleted");

        LifeUsers users = resolveLifeUsers(currentUser);
        return new LifeConsoleBowelMutationResponse(
                eventDto,
                buildBowelSummary(range, users, currentUser.libraryId())
        );
    }

    /**
     * Round 7: 更新媒体位置。仅记录所有者可修改。label 缺失时服务端逆地理编码回填。
     */
    @Transactional
    public LifeConsoleTodayResponse updateMediaLocation(String mediaId, UpdateLocationRequest request, AuthenticatedUser currentUser) {
        MediaEntity media = mediaRepository.findByIdAndLibraryIdAndDeletedAtIsNull(mediaId, currentUser.libraryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Media was not found."));
        if (!currentUser.userId().equals(media.getRecordOwnerUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "You can only update your own media location.");
        }
        Double lat = request.latitude();
        Double lng = request.longitude();
        String label = request.locationLabel();
        if (label == null || label.isBlank()) {
            label = geocodingService.reverseGeocode(lat, lng);
        }
        media.setLatitude(lat);
        media.setLongitude(lng);
        media.setLocationLabel(label);
        mediaRepository.save(media);
        notifyLifeConsoleChanged(currentUser.libraryId(), currentUser.userId(), "media_location_updated");
        return getToday(null, DEFAULT_ZONE_ID, currentUser);
    }

    /**
     * Round 7: 更新大便事件位置。仅事件所有者可修改。label 缺失时服务端逆地理编码回填。
     */
    @Transactional
    public LifeConsoleBowelMutationResponse updateBowelEventLocation(String eventId, UpdateLocationRequest request, AuthenticatedUser currentUser) {
        BowelEventEntity event = bowelEventRepository.findByIdAndLibraryId(eventId, currentUser.libraryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Bowel event not found."));
        if (event.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Bowel event not found.");
        }
        if (!currentUser.userId().equals(event.getUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "You can only update your own bowel event location.");
        }
        Double lat = request.latitude();
        Double lng = request.longitude();
        String label = request.locationLabel();
        if (label == null || label.isBlank()) {
            label = geocodingService.reverseGeocode(lat, lng);
        }
        event.setLatitude(lat);
        event.setLongitude(lng);
        event.setLocationLabel(label);
        bowelEventRepository.save(event);
        notifyLifeConsoleChanged(currentUser.libraryId(), currentUser.userId(), "bowel_location_updated");
        ZoneId zone = parseZoneId(null);
        LocalDate today = LocalDate.now(zone);
        LifeUsers users = resolveLifeUsers(currentUser);
        return new LifeConsoleBowelMutationResponse(
                toBowelEventDto(event),
                buildBowelSummary(dateRange(today, zone), users, currentUser.libraryId())
        );
    }

    private LifeConsoleTodayResponse buildTodayResponse(
            LocalDate date,
            ZoneId zone,
            DateRange dateRange,
            LifeUsers users,
            AuthenticatedUser currentUser
    ) {
        return new LifeConsoleTodayResponse(
                date.toString(),
                zone.getId(),
                toUserDto(users.currentUser()),
                users.partner() == null ? null : toUserDto(users.partner()),
                buildMediaSlot(LifeConsoleCategory.PERSON, users.currentUser().getId(), true, dateRange, YearMonth.from(date), currentUser.libraryId()),
                buildMediaSlot(LifeConsoleCategory.PERSON, users.partnerUserId(), false, dateRange, YearMonth.from(date), currentUser.libraryId()),
                buildMediaSlot(LifeConsoleCategory.MEAL, users.currentUser().getId(), true, dateRange, YearMonth.from(date), currentUser.libraryId()),
                buildMediaSlot(LifeConsoleCategory.MEAL, users.partnerUserId(), false, dateRange, YearMonth.from(date), currentUser.libraryId()),
                buildBowelSummary(dateRange, users, currentUser.libraryId())
        );
    }

    private void notifyLifeConsoleChanged(String libraryId, String actorUserId, String reason) {
        PushDispatchSupport.afterCommitAsync(() -> pushNotificationService.notifyLifeConsoleChanged(libraryId, actorUserId, reason));
    }

    private LifeConsoleMediaSlotDto buildMediaSlot(
            LifeConsoleCategory category,
            String ownerUserId,
            boolean editable,
            DateRange dateRange,
            YearMonth yearMonth,
            String libraryId
    ) {
        if (ownerUserId == null) {
            return new LifeConsoleMediaSlotDto(category.name(), null, false, List.of());
        }
        // Round 8 Bug 修复: 优先查未删除的，查不到就查已软删除的 (可能被误删过)，
        // 但 buildMediaSlot 只读不写，所以只查未删除的即可。如果 album 被软删除，
        // ensureSystemAlbum 会在下次 addMedia 时复活它。
        AlbumEntity album = albumRepository.findByLibraryIdAndSystemKeyAndDomainAndDeletedAtIsNull(libraryId, category.albumSystemKey(), "life").orElse(null);
        if (album == null) {
            // buildMediaSlot 运行在只读事务中，不能执行写入（复活软删 album 不会落库）。
            // 软删 album 的复活由 ensureSystemAlbum 在 addMedia（写事务）时处理。
            // 这里直接回退查该用户当天所有 media。
            return buildFallbackMediaSlot(category, ownerUserId, editable, dateRange, libraryId);
        }
        PostEntity monthlySmallAlbum = postRepository
                .findByLibraryIdAndAlbumIdAndSystemKeyAndDomainAndDeletedAtIsNull(libraryId, album.getId(), yearMonth.toString(), "life")
                .orElse(null);
        if (monthlySmallAlbum == null) {
            // Round 8: 月度 small_album 不存在, 回退查该用户当天所有 media
            return buildFallbackMediaSlot(category, ownerUserId, editable, dateRange, libraryId);
        }

        // Round 8 第十五轮: 按 media 的 displayTimeMillis 过滤当日 slot, 而不是 relation.createdAt.
        // 这样今天上传的昨天拍的照片会出现在昨天的历史记录里, 而不是今天.
        List<PostMediaEntity> allRelations = postMediaRepository
                .findByLibraryIdAndPostIdOrderBySortOrderAsc(libraryId, monthlySmallAlbum.getId());
        List<String> allMediaIds = allRelations.stream().map(PostMediaEntity::getMediaId).toList();
        Map<String, MediaEntity> allMediaById = allMediaIds.isEmpty()
                ? Map.of()
                : mediaRepository.findByLibraryIdAndIdInAndDeletedAtIsNull(libraryId, allMediaIds)
                        .stream()
                        .collect(Collectors.toMap(MediaEntity::getId, Function.identity()));
        List<PostMediaEntity> todayRelations = allRelations.stream()
                .filter(relation -> {
                    MediaEntity media = allMediaById.get(relation.getMediaId());
                    if (media == null) return false;
                    long mediaTime = resolveMediaDisplayTime(media, relation);
                    return mediaTime >= dateRange.startMillis() && mediaTime < dateRange.endMillis();
                })
                .toList();
        if (todayRelations.isEmpty()) {
            // Round 8: life domain 当天没数据, 回退查该用户当天所有 media
            return buildFallbackMediaSlot(category, ownerUserId, editable, dateRange, libraryId);
        }

        Map<String, MediaEntity> mediaById = allMediaById.values().stream()
                .filter(media -> mediaBelongsToUser(media, ownerUserId))
                .collect(Collectors.toMap(MediaEntity::getId, Function.identity()));

        List<MediaDto> mediaDtos = new ArrayList<>();
        for (PostMediaEntity relation : todayRelations) {
            MediaEntity media = mediaById.get(relation.getMediaId());
            if (media != null && mediaBelongsToUser(media, ownerUserId)) {
                mediaDtos.add(contentMapper.toMediaDto(media, List.of(monthlySmallAlbum.getId())));
            }
        }
        // Round 8: 如果 life domain 当天该用户没数据, 回退查该用户当天所有 media (含 photo domain)
        if (mediaDtos.isEmpty()) {
            return buildFallbackMediaSlot(category, ownerUserId, editable, dateRange, libraryId);
        }
        return new LifeConsoleMediaSlotDto(category.name(), ownerUserId, editable, mediaDtos);
    }

    /**
     * Round 8 第十八轮: 修复 PERSON/MEAL 串类 bug.
     *
     * 之前实现: 调 findByLibraryIdAndUserIdAndDisplayTimeRange 不带 category 过滤,
     *   该查询按 MediaEntity.recordOwnerUserId + displayTimeMillis 过滤,
     *   但 MediaEntity 本身不带 category (PERSON/MEAL), 导致 PERSON slot 的 fallback
     *   会查出 MEAL 的 media, 反之亦然. 用户反馈"在吃饭里上传一张, 今日页人物和吃饭都有了".
     *
     * 现在实现: 严格按 category 查 life domain 的 small_album 关联.
     *   1. 查 life domain 该 category 的 system album (album.systemKey = "PERSON" / "MEAL")
     *   2. 查该 album 下所有 small_album (post) 的 post_media 关联
     *   3. filter 当天 dateRange (按 displayTimeMillis) + 该用户
     *   4. 不再回退查 photo domain, 避免串类
     *
     * 代价: partner 没主动上传到 life 模块时, 看不到 photo domain 的照片.
     * 但用户明确要求 PERSON/MEAL 不串, 这是更重要的诉求.
     */
    private LifeConsoleMediaSlotDto buildFallbackMediaSlot(
            LifeConsoleCategory category,
            String ownerUserId,
            boolean editable,
            DateRange dateRange,
            String libraryId
    ) {
        AlbumEntity album = albumRepository.findByLibraryIdAndSystemKeyAndDomainAndDeletedAtIsNull(
                libraryId, category.albumSystemKey(), "life").orElse(null);
        if (album == null) {
            return new LifeConsoleMediaSlotDto(category.name(), ownerUserId, editable, List.of());
        }
        List<PostEntity> posts = postRepository.findByLibraryIdAndAlbumIdAndDomainAndDeletedAtIsNullOrderByDisplayTimeMillisDescUpdatedAtDesc(
                libraryId, album.getId(), "life");
        if (posts.isEmpty()) {
            return new LifeConsoleMediaSlotDto(category.name(), ownerUserId, editable, List.of());
        }
        List<String> postIds = posts.stream().map(PostEntity::getId).toList();
        List<PostMediaEntity> relations = postMediaRepository.findByLibraryIdAndPostIdIn(libraryId, postIds);
        if (relations.isEmpty()) {
            return new LifeConsoleMediaSlotDto(category.name(), ownerUserId, editable, List.of());
        }
        List<String> mediaIds = relations.stream().map(PostMediaEntity::getMediaId).distinct().toList();
        Map<String, MediaEntity> mediaById = mediaRepository.findByLibraryIdAndIdInAndDeletedAtIsNull(libraryId, mediaIds)
                .stream()
                .collect(Collectors.toMap(MediaEntity::getId, Function.identity()));
        List<MediaDto> mediaDtos = relations.stream()
                .filter(relation -> {
                    MediaEntity media = mediaById.get(relation.getMediaId());
                    if (media == null) return false;
                    if (!mediaBelongsToUser(media, ownerUserId)) return false;
                    long mediaTime = resolveMediaDisplayTime(media, relation);
                    return mediaTime >= dateRange.startMillis() && mediaTime < dateRange.endMillis();
                })
                .map(relation -> {
                    MediaEntity media = mediaById.get(relation.getMediaId());
                    return contentMapper.toMediaDto(media, List.of());
                })
                .toList();
        return new LifeConsoleMediaSlotDto(category.name(), ownerUserId, editable, mediaDtos);
    }

    private LifeConsoleBowelSummaryDto buildBowelSummary(DateRange dateRange, LifeUsers users, String libraryId) {
        List<String> userIds = new ArrayList<>();
        userIds.add(users.currentUser().getId());
        if (users.partnerUserId() != null) {
            userIds.add(users.partnerUserId());
        }

        Map<String, List<BowelEventEntity>> eventsByUserId = bowelEventRepository
                .findByLibraryIdAndOccurredAtMillisGreaterThanEqualAndOccurredAtMillisLessThanOrderByOccurredAtMillisAsc(
                        libraryId,
                        dateRange.startMillis(),
                        dateRange.endMillis()
                )
                .stream()
                .filter(event -> userIds.contains(event.getUserId()))
                .collect(Collectors.groupingBy(
                        BowelEventEntity::getUserId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<LifeConsoleBowelUserSummaryDto> summaries = new ArrayList<>();
        for (String userId : userIds) {
            List<BowelEventEntity> events = eventsByUserId.getOrDefault(userId, List.of());
            // FR-18: pick the latest event's location label (events may not be sorted, find max by occurredAtMillis)
            BowelEventEntity latestEvent = events.stream()
                    .max(Comparator.comparingLong(BowelEventEntity::getOccurredAtMillis))
                    .orElse(null);
            Long latest = latestEvent == null ? null : latestEvent.getOccurredAtMillis();
            String latestLocationLabel = latestEvent == null ? null : latestEvent.getLocationLabel();
            // Round 7: 当日所有大便事件列表（按时间倒序），用于今日页大便页每条单独展示
            List<LifeConsoleBowelEventDto> eventDtos = events.stream()
                    .sorted(Comparator.comparingLong(BowelEventEntity::getOccurredAtMillis).reversed())
                    .map(this::toBowelEventDto)
                    .toList();
            summaries.add(new LifeConsoleBowelUserSummaryDto(
                    userId,
                    events.size(),
                    latest,
                    events.stream().map(BowelEventEntity::getOccurredAtMillis).toList(),
                    latestLocationLabel,
                    eventDtos
            ));
        }
        return new LifeConsoleBowelSummaryDto(summaries);
    }

    private List<LifeConsoleHistoryDayDto> buildHistoryDays(
            LifeConsoleCategory category,
            LifeUsers users,
            String libraryId,
            ZoneId zone,
            int limitDays
    ) {
        LocalDate today = LocalDate.now(zone);
        // Round 8 第十九轮: 恢复历史页包含今天.
        // 用户反馈: "今日痕迹里的历史记录, 又没有同步最新的记录了, 之前都有的, 就是我今天上传了的也要在历史记录里出现."
        // 第十八轮 u4 排除了今天导致历史页看不到今天上传的媒体, 与用户期望相悖.
        // 现在恢复 earliestDate..today (含 today), displayTimeMillis=今天的媒体同时进今日页和历史页的今天.
        // 双计是用户可接受的 (今日页是当日入口, 历史页是历史入口, 两个入口都该能看到今天的媒体).
        LocalDate earliestDate = LocalDate.now(zone).minusDays(limitDays - 1L);
        long earliestMillis = earliestDate.atStartOfDay(zone).toInstant().toEpochMilli();
        long endMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

        Map<LocalDate, List<HistoryMediaEntry>> selfByDay = new LinkedHashMap<>();
        Map<LocalDate, List<HistoryMediaEntry>> partnerByDay = new LinkedHashMap<>();

        // buildHistoryDays 运行在只读事务中，不能执行写入（复活软删 album 不会落库）。
        // 软删 album 的复活由 ensureSystemAlbum 在 addMedia（写事务）时处理。
        // 这里即使 album 被软删除，其下的 posts/media 关联仍可读取，直接用于展示历史。
        AlbumEntity album = albumRepository.findByLibraryIdAndSystemKeyAndDomainAndDeletedAtIsNull(libraryId, category.albumSystemKey(), "life")
                .or(() -> albumRepository.findByLibraryIdAndSystemKeyAndDomain(libraryId, category.albumSystemKey(), "life"))
                .orElse(null);
        // life domain 有 album 时, 查 small_album_media 关联
        if (album != null) {
            List<PostEntity> posts = postRepository.findByLibraryIdAndAlbumIdAndDomainAndDeletedAtIsNullOrderByDisplayTimeMillisDescUpdatedAtDesc(
                    libraryId,
                    album.getId(),
                    "life"
            );
            if (!posts.isEmpty()) {
                List<String> postIds = posts.stream().map(PostEntity::getId).toList();
                Map<String, PostEntity> postsById = posts.stream().collect(Collectors.toMap(PostEntity::getId, Function.identity()));
                List<PostMediaEntity> relations = postMediaRepository.findByLibraryIdAndPostIdIn(libraryId, postIds);
                if (!relations.isEmpty()) {
                    List<String> mediaIds = relations.stream().map(PostMediaEntity::getMediaId).distinct().toList();
                    Map<String, MediaEntity> mediaById = mediaRepository.findByLibraryIdAndIdInAndDeletedAtIsNull(libraryId, mediaIds)
                            .stream()
                            .collect(Collectors.toMap(MediaEntity::getId, Function.identity()));

                    relations.stream()
                            .sorted(Comparator.comparing((PostMediaEntity relation) -> {
                                MediaEntity media = mediaById.get(relation.getMediaId());
                                return media == null ? Long.MIN_VALUE : resolveHistoryTimeMillis(relation, media);
                            }).reversed())
                            .forEach(relation -> {
                                MediaEntity media = mediaById.get(relation.getMediaId());
                                PostEntity post = postsById.get(relation.getPostId());
                                if (media == null || post == null) {
                                    return;
                                }
                                long effectiveTimeMillis = resolveHistoryTimeMillis(relation, media);
                                LocalDate mediaDate = Instant.ofEpochMilli(effectiveTimeMillis).atZone(zone).toLocalDate();
                                if (mediaDate.isBefore(earliestDate)) {
                                    return;
                                }
                                // Round 8 第十九轮: 恢复包含今天, 排除未来日期.
                                // mediaDate > today 才跳过 (未来时间戳异常数据), == today 保留.
                                if (mediaDate.isAfter(today)) {
                                    return;
                                }
                                HistoryMediaEntry entry = new HistoryMediaEntry(media, effectiveTimeMillis);
                                if (mediaBelongsToUser(media, users.currentUser().getId())) {
                                    selfByDay.computeIfAbsent(mediaDate, ignored -> new ArrayList<>()).add(entry);
                                } else if (mediaBelongsToUser(media, users.partnerUserId())) {
                                    partnerByDay.computeIfAbsent(mediaDate, ignored -> new ArrayList<>()).add(entry);
                                }
                            });
                }
            }
        }

        // Round 8 第十八轮: 修复 PERSON/MEAL 串类 bug.
        // 之前: fillMissingDaysFromAllMedia 不带 category 过滤, 会把 MEAL 的 media 填到 PERSON 的历史日期, 反之亦然.
        // 现在: 严格按 category 查 life domain small_album 关联, 不再回退查 photo domain.
        fillMissingDaysFromAllMedia(selfByDay, category, users.currentUser().getId(), libraryId, earliestDate, today, earliestMillis, endMillis, zone);
        if (users.partnerUserId() != null) {
            fillMissingDaysFromAllMedia(partnerByDay, category, users.partnerUserId(), libraryId, earliestDate, today, earliestMillis, endMillis, zone);
        }

        List<LocalDate> orderedDates = new ArrayList<>();
        orderedDates.addAll(selfByDay.keySet());
        partnerByDay.keySet().forEach(date -> {
            if (!orderedDates.contains(date)) {
                orderedDates.add(date);
            }
        });
        orderedDates.sort(Comparator.reverseOrder());

        return orderedDates.stream()
                .map(date -> {
                    List<HistoryMediaEntry> selfEntries = selfByDay.getOrDefault(date, List.of()).stream()
                            .sorted(Comparator.comparingLong(HistoryMediaEntry::effectiveTimeMillis).reversed())
                            .toList();
                    List<HistoryMediaEntry> partnerEntries = partnerByDay.getOrDefault(date, List.of()).stream()
                            .sorted(Comparator.comparingLong(HistoryMediaEntry::effectiveTimeMillis).reversed())
                            .toList();
                    // FR-18: day-level representative location = the latest media's locationLabel (self takes priority over partner)
                    String dayLocationLabel = pickDayLocationLabel(selfEntries, partnerEntries);
                    return new LifeConsoleHistoryDayDto(
                            date.toString(),
                            formatHistoryDate(date),
                            selfEntries.stream()
                                    .map(entry -> withDisplayTime(contentMapper.toMediaDto(entry.media(), List.of()), entry.effectiveTimeMillis()))
                                    .toList(),
                            partnerEntries.stream()
                                    .map(entry -> withDisplayTime(contentMapper.toMediaDto(entry.media(), List.of()), entry.effectiveTimeMillis()))
                                    .toList(),
                            dayLocationLabel
                    );
                })
                .toList();
    }

    /**
     * Round 8 第十八轮: 用 life domain 该 category 的 media 填补没数据的日期.
     *
     * 之前: 调 findByLibraryIdAndUserIdAndDisplayTimeRange 不带 category 过滤,
     *   会把 MEAL 的 media 填到 PERSON 的历史日期, 反之亦然.
     * 现在: 严格按 category 查 life domain 的 small_album 关联, 不再回退查 photo domain.
     *
     * 不覆盖已有 life domain 数据, 仅在缺失日期补充.
     */
    private void fillMissingDaysFromAllMedia(
            Map<LocalDate, List<HistoryMediaEntry>> byDay,
            LifeConsoleCategory category,
            String userId,
            String libraryId,
            LocalDate earliestDate,
            LocalDate today,
            long earliestMillis,
            long endMillis,
            ZoneId zone
    ) {
        AlbumEntity album = albumRepository.findByLibraryIdAndSystemKeyAndDomainAndDeletedAtIsNull(
                libraryId, category.albumSystemKey(), "life").orElse(null);
        if (album == null) {
            return;
        }
        List<PostEntity> posts = postRepository.findByLibraryIdAndAlbumIdAndDomainAndDeletedAtIsNullOrderByDisplayTimeMillisDescUpdatedAtDesc(
                libraryId, album.getId(), "life");
        if (posts.isEmpty()) {
            return;
        }
        List<String> postIds = posts.stream().map(PostEntity::getId).toList();
        List<PostMediaEntity> relations = postMediaRepository.findByLibraryIdAndPostIdIn(libraryId, postIds);
        if (relations.isEmpty()) {
            return;
        }
        List<String> mediaIds = relations.stream().map(PostMediaEntity::getMediaId).distinct().toList();
        Map<String, MediaEntity> mediaById = mediaRepository.findByLibraryIdAndIdInAndDeletedAtIsNull(libraryId, mediaIds)
                .stream()
                .collect(Collectors.toMap(MediaEntity::getId, Function.identity()));
        // filter 该用户的 media, 按有效时间 (displayTimeMillis 优先) 分组到对应日期
        List<Map.Entry<PostMediaEntity, MediaEntity>> userOwnedEntries = relations.stream()
                .map(relation -> {
                    MediaEntity media = mediaById.get(relation.getMediaId());
                    return media != null && mediaBelongsToUser(media, userId)
                            ? Map.entry(relation, media)
                            : null;
                })
                .filter(Objects::nonNull)
                .toList();
        for (Map.Entry<PostMediaEntity, MediaEntity> entry : userOwnedEntries) {
            PostMediaEntity relation = entry.getKey();
            MediaEntity media = entry.getValue();
            long effectiveTimeMillis = resolveHistoryTimeMillis(relation, media);
            if (effectiveTimeMillis <= 0L) {
                continue;
            }
            LocalDate mediaDate = Instant.ofEpochMilli(effectiveTimeMillis).atZone(zone).toLocalDate();
            // Round 8 第十九轮: 恢复包含今天, 排除未来日期.
            if (mediaDate.isBefore(earliestDate) || mediaDate.isAfter(today)) {
                continue;
            }
            // 该日期 life domain 已有数据, 不覆盖
            if (byDay.containsKey(mediaDate) && !byDay.get(mediaDate).isEmpty()) {
                continue;
            }
            byDay.computeIfAbsent(mediaDate, ignored -> new ArrayList<>())
                    .add(new HistoryMediaEntry(media, effectiveTimeMillis));
        }
    }

    /**
     * FR-18: pick the representative location label for a day.
     * Strategy: among all media of the day (self + partner), take the latest one (by effectiveTimeMillis)
     * that has a non-blank locationLabel. Self entries take priority when timestamps tie.
     */
    private String pickDayLocationLabel(List<HistoryMediaEntry> selfEntries, List<HistoryMediaEntry> partnerEntries) {
        HistoryMediaEntry latest = null;
        for (HistoryMediaEntry entry : selfEntries) {
            if (entry.media().getLocationLabel() == null || entry.media().getLocationLabel().isBlank()) {
                continue;
            }
            if (latest == null || entry.effectiveTimeMillis() > latest.effectiveTimeMillis()) {
                latest = entry;
            }
        }
        for (HistoryMediaEntry entry : partnerEntries) {
            if (entry.media().getLocationLabel() == null || entry.media().getLocationLabel().isBlank()) {
                continue;
            }
            if (latest == null || entry.effectiveTimeMillis() > latest.effectiveTimeMillis()) {
                latest = entry;
            }
        }
        return latest == null ? null : latest.media().getLocationLabel();
    }

    private long resolveHistoryTimeMillis(PostMediaEntity relation, MediaEntity media) {
        // Round 8 第十五轮: 优先用 media 的 displayTimeMillis, 这样历史页按拍摄时间归组.
        // 之前优先用 relation.createdAt, 导致今天上传的昨天照片归到今天.
        if (media.getDisplayTimeMillis() != null && media.getDisplayTimeMillis() > 0L) {
            return media.getDisplayTimeMillis();
        }
        if (media.getCapturedAtMillis() != null && media.getCapturedAtMillis() > 0L) {
            return media.getCapturedAtMillis();
        }
        if (media.getImportedAtMillis() != null && media.getImportedAtMillis() > 0L) {
            return media.getImportedAtMillis();
        }
        if (relation.getCreatedAt() != null) {
            return relation.getCreatedAt().toEpochMilli();
        }
        return 0L;
    }

    private boolean mediaBelongsToUser(MediaEntity media, String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (Objects.equals(media.getRecordOwnerUserId(), userId)) {
            return true;
        }
        return media.getRecordOwnerUserId() == null
                && Objects.equals(media.getUploadedByUserId(), userId);
    }

    private MediaDto withDisplayTime(MediaDto source, long displayTimeMillis) {
        return new MediaDto(
                source.mediaId(),
                source.mediaType(),
                source.url(),
                source.previewUrl(),
                source.originalUrl(),
                source.videoUrl(),
                source.coverUrl(),
                source.mimeType(),
                source.sizeBytes(),
                source.width(),
                source.height(),
                source.aspectRatio(),
                source.durationMillis(),
                displayTimeMillis,
                source.capturedAtMillis(),
                source.importedAtMillis(),
                source.displayTimeSource(),
                source.recordOwnerUserId(),
                source.uploadedByUserId(),
                source.smallAlbumIds(),
                source.access(),
                source.latitude(),
                source.longitude(),
                source.locationLabel()
        );
    }

    private record HistoryMediaEntry(
            MediaEntity media,
            long effectiveTimeMillis
    ) {
    }

    private List<LifeConsoleBowelHistoryDayDto> buildBowelHistoryDays(
            LifeUsers users,
            String libraryId,
            ZoneId zone,
            int limitDays
    ) {
        LocalDate today = LocalDate.now(zone);
        LocalDate earliestDate = today.minusDays(limitDays - 1L);
        DateRange range = new DateRange(
                earliestDate.atStartOfDay(zone).toInstant().toEpochMilli(),
                today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        );

        List<String> userIds = new ArrayList<>();
        userIds.add(users.currentUser().getId());
        if (users.partnerUserId() != null) {
            userIds.add(users.partnerUserId());
        }

        Map<LocalDate, List<BowelEventEntity>> eventsByDay = bowelEventRepository
                .findByLibraryIdAndOccurredAtMillisGreaterThanEqualAndOccurredAtMillisLessThanOrderByOccurredAtMillisAsc(
                        libraryId,
                        range.startMillis(),
                        range.endMillis()
                )
                .stream()
                .filter(event -> userIds.contains(event.getUserId()))
                .collect(Collectors.groupingBy(
                        event -> Instant.ofEpochMilli(event.getOccurredAtMillis()).atZone(zone).toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<LocalDate> orderedDates = new ArrayList<>(eventsByDay.keySet());
        orderedDates.sort(Comparator.reverseOrder());

        return orderedDates.stream()
                .map(date -> {
                    if (date.isAfter(today)) {
                        return null;
                    }
                    List<BowelEventEntity> events = eventsByDay.getOrDefault(date, List.of());
                    List<LifeConsoleBowelUserSummaryDto> summaries = userIds.stream()
                            .map(userId -> {
                                List<BowelEventEntity> userEvents = events.stream()
                                        .filter(event -> Objects.equals(event.getUserId(), userId))
                                        .sorted(Comparator.comparingLong(BowelEventEntity::getOccurredAtMillis))
                                        .toList();
                                List<Long> times = userEvents.stream()
                                        .map(BowelEventEntity::getOccurredAtMillis)
                                        .toList();
                                BowelEventEntity latestEvent = userEvents.isEmpty() ? null : userEvents.get(userEvents.size() - 1);
                                String latestLocationLabel = latestEvent == null ? null : latestEvent.getLocationLabel();
                                // Round 8 Bug 修复: 之前用向后兼容构造器 (5参数), events 永远是 null,
                                // 导致历史页大便卡片无法按 event 拆开。这里改用 6 参数构造器填充 events。
                                List<LifeConsoleBowelEventDto> eventDtos = userEvents.stream()
                                        .sorted(Comparator.comparingLong(BowelEventEntity::getOccurredAtMillis).reversed())
                                        .map(this::toBowelEventDto)
                                        .toList();
                                return new LifeConsoleBowelUserSummaryDto(
                                        userId,
                                        times.size(),
                                        times.isEmpty() ? null : times.get(times.size() - 1),
                                        times,
                                        latestLocationLabel,
                                        eventDtos
                                );
                            })
                            .filter(summary -> summary.count() > 0)
                            .toList();
                    // FR-18: day-level representative location = the latest event's locationLabel
                    String dayLocationLabel = events.stream()
                            .max(Comparator.comparingLong(BowelEventEntity::getOccurredAtMillis))
                            .map(BowelEventEntity::getLocationLabel)
                            .filter(label -> label != null && !label.isBlank())
                            .orElse(null);
                    return new LifeConsoleBowelHistoryDayDto(
                            date.toString(),
                            formatHistoryDate(date),
                            summaries,
                            dayLocationLabel
                    );
                })
                .filter(Objects::nonNull)
                .filter(day -> !day.users().isEmpty())
                .toList();
    }

    private AlbumEntity ensureSystemAlbum(String libraryId, LifeConsoleCategory category) {
        // Round 8 Bug 修复: 优先查未删除的，查不到就查已软删除的并复活它。
        // 原因: uk_albums_library_system_key 唯一约束不包含 deleted_at，
        // 软删除的 album 仍占着 (library_id, system_key, domain) 这个 key，
        // 直接 INSERT 新的会撞约束报 "Unexpected server error"。
        AlbumEntity album = albumRepository.findByLibraryIdAndSystemKeyAndDomainAndDeletedAtIsNull(libraryId, category.albumSystemKey(), "life").orElse(null);
        if (album == null) {
            // 查已软删除的，复活它
            album = albumRepository.findByLibraryIdAndSystemKeyAndDomain(libraryId, category.albumSystemKey(), "life").orElse(null);
            if (album != null) {
                album.setDeletedAt(null);
            }
        }
        if (album == null) {
            album = new AlbumEntity();
            album.setId(IdGenerator.newId("album"));
            album.setLibraryId(libraryId);
            album.setSystemKey(category.albumSystemKey());
            album.setTitle(category.albumTitle());
            album.setSubtitle("");
            album.setCoverMediaId(null);
        }
        album.setDomain("life");
        album.setIncludeInPhotoFeed(category.includeInPhotoFeed());
        if (album.getTitle() == null || album.getTitle().isBlank()) {
            album.setTitle(category.albumTitle());
        }
        return albumRepository.save(album);
    }

    private PostEntity ensureMonthlySmallAlbum(String libraryId, AlbumEntity album, YearMonth yearMonth, ZoneId zone) {
        String systemKey = yearMonth.toString();
        PostEntity post = postRepository
                .findByLibraryIdAndAlbumIdAndSystemKeyAndDomainAndDeletedAtIsNull(libraryId, album.getId(), systemKey, "life")
                .orElse(null);
        if (post != null) {
            return post;
        }
        // Round 8 Bug 修复: 月度 post 可能被系统相册删除时级联软删了，
        // 但 uk_small_albums_library_album_system_key 唯一约束不含 deleted_at，
        // 直接 INSERT 新的会撞约束。这里复活软删的 post。
        PostEntity softDeleted = postRepository
                .findByLibraryIdAndAlbumIdAndSystemKeyAndDomain(libraryId, album.getId(), systemKey, "life")
                .orElse(null);
        if (softDeleted != null && softDeleted.getDeletedAt() != null) {
            softDeleted.setDeletedAt(null);
            return postRepository.save(softDeleted);
        }
        LocalDate firstDay = yearMonth.atDay(1);
        PostEntity created = new PostEntity();
        created.setId(IdGenerator.newId("small_album"));
        created.setLibraryId(libraryId);
        created.setAlbumId(album.getId());
        created.setSystemKey(systemKey);
        created.setTitle(monthTitle(yearMonth));
        created.setSummary("");
        created.setContributorLabel("Life Console");
        created.setDisplayTimeMillis(firstDay.atStartOfDay(zone).toInstant().toEpochMilli());
        created.setEventStartedAtMillis(firstDay.atStartOfDay(zone).toInstant().toEpochMilli());
        created.setEventEndedAtMillis(yearMonth.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1);
        created.setDisplayTimeSource("MANUAL");
        created.setDomain("life");
        created.setCoverMediaId(null);
        return postRepository.save(created);
    }

    private LifeUsers resolveLifeUsers(AuthenticatedUser currentUser) {
        UserEntity user = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_UNAUTHORIZED, "Current user does not exist."));
        List<SharedLibraryMemberEntity> members = sharedLibraryMemberRepository.findByLibraryId(currentUser.libraryId());
        List<String> memberUserIds = members.stream()
                .map(SharedLibraryMemberEntity::getUserId)
                .distinct()
                .toList();
        Map<String, UserEntity> usersById = userRepository.findByIdIn(memberUserIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        UserEntity partner = memberUserIds.stream()
                .filter(userId -> !userId.equals(currentUser.userId()))
                .map(usersById::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(UserEntity::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .findFirst()
                .orElse(null);
        return new LifeUsers(user, partner);
    }

    private LifeConsoleUserDto toUserDto(UserEntity user) {
        return new LifeConsoleUserDto(
                user.getId(),
                user.getAccount(),
                user.getDisplayName(),
                normalizeNullable(user.getAvatarUrl()) == null ? null : "/api/auth/avatar/" + user.getId()
        );
    }

    private LifeConsoleBowelEventDto toBowelEventDto(BowelEventEntity event) {
        return new LifeConsoleBowelEventDto(
                event.getId(),
                event.getUserId(),
                event.getOccurredAtMillis(),
                event.getLatitude(),
                event.getLongitude(),
                event.getLocationLabel()
        );
    }

    private List<String> normalizedDistinctMediaIds(List<String> mediaIds) {
        List<String> normalized = mediaIds.stream()
                .map(mediaId -> mediaId == null ? "" : mediaId.trim())
                .filter(mediaId -> !mediaId.isBlank())
                .toList();
        if (normalized.size() != mediaIds.size() || new HashSet<>(normalized).size() != normalized.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.SMALL_ALBUM_MEDIA_ORDER_INVALID, "mediaIds contains invalid or duplicate ids.");
        }
        return normalized;
    }

    private ZoneId parseZoneId(String rawZoneId) {
        String value = rawZoneId == null || rawZoneId.isBlank() ? DEFAULT_ZONE_ID : rawZoneId.trim();
        try {
            return ZoneId.of(value);
        } catch (DateTimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "zoneId is invalid.");
        }
    }

    private LocalDate parseDate(String rawDate, ZoneId zone) {
        if (rawDate == null || rawDate.isBlank()) {
            return LocalDate.now(zone);
        }
        try {
            return LocalDate.parse(rawDate.trim());
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "date must be YYYY-MM-DD.");
        }
    }

    private DateRange dateRange(LocalDate date, ZoneId zone) {
        long startMillis = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long endMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return new DateRange(startMillis, endMillis);
    }

    private String monthTitle(YearMonth yearMonth) {
        return String.format(Locale.ROOT, "%04d年%02d月", yearMonth.getYear(), yearMonth.getMonthValue());
    }

    private String formatHistoryDate(LocalDate date) {
        return String.format(Locale.CHINA, "%d月%d日", date.getMonthValue(), date.getDayOfMonth());
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record DateRange(long startMillis, long endMillis) {
    }

    private record LifeUsers(UserEntity currentUser, UserEntity partner) {
        private String partnerUserId() {
            return partner == null ? null : partner.getId();
        }
    }
}
