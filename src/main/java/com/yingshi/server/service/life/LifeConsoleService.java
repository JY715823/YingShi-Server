package com.yingshi.server.service.life;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.BowelEventEntity;
import com.yingshi.server.domain.MediaEntity;
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
import com.yingshi.server.repository.BowelEventRepository;
import com.yingshi.server.repository.MediaRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LifeConsoleService {

    private static final String DEFAULT_ZONE_ID = "Asia/Shanghai";
    private static final Logger log = LoggerFactory.getLogger(LifeConsoleService.class);

    private final MediaRepository mediaRepository;
    private final BowelEventRepository bowelEventRepository;
    private final UserRepository userRepository;
    private final SharedLibraryMemberRepository sharedLibraryMemberRepository;
    private final ContentMapper contentMapper;
    private final TrashService trashService;
    private final PushNotificationService pushNotificationService;
    private final GeocodingService geocodingService;

    public LifeConsoleService(
            MediaRepository mediaRepository,
            BowelEventRepository bowelEventRepository,
            UserRepository userRepository,
            SharedLibraryMemberRepository sharedLibraryMemberRepository,
            ContentMapper contentMapper,
            TrashService trashService,
            PushNotificationService pushNotificationService,
            GeocodingService geocodingService
    ) {
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
        // 诊断日志: life addMedia 操作前的 photoFeedVersion
        Long photoFeedVersionBefore = mediaRepository.findLatestUpdatedAtByLibraryIdAndDeletedAtIsNullAndDomainNotLife(libraryId)
                .map(Instant::toEpochMilli).orElse(0L);
        log.warn("addMedia: BEFORE photoFeedVersion={} libraryId={} category={} mediaIds={}", photoFeedVersionBefore, libraryId, category, mediaIds);
        List<MediaEntity> mediaItems = mediaRepository.findByLibraryIdAndIdInAndDeletedAtIsNull(libraryId, mediaIds);
        if (mediaItems.size() != mediaIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.MEDIA_NOT_FOUND, "One or more mediaIds do not exist in the shared library.");
        }

        ZoneId zone = parseZoneId(zoneId);
        LocalDate today = LocalDate.now(zone);

        // P1-1 改造: life 媒体不再放进相册/小相册，直接在 MediaEntity 上设置 domain + lifeCategory.
        // 之前: 通过 album + post(small_album) + post_media 三层结构关联，导致：
        //   1) 自动建月度日期目录出现在相册模块；
        //   2) 回收站也包含 life 媒体；
        //   3) life 操作触发 photoFeedVersion 上涨（因为更新了 post/post_media）。
        // 现在: 仅修改 media 的 domain + lifeCategory + ownership，不触碰 album/post/post_media。
        // 今日页/历史页通过 mediaRepository.findLifeMediaByCategoryAndDisplayTimeRange 直接查询 media 表。
        for (MediaEntity media : mediaItems) {
            media.setRecordOwnerUserId(currentUser.userId());
            if (media.getUploadedByUserId() == null || media.getUploadedByUserId().isBlank()) {
                media.setUploadedByUserId(currentUser.userId());
            }
            media.setDomain("life");
            media.setLifeCategory(category.name());
        }
        mediaRepository.saveAll(mediaItems);

        // 推送通知携带最新上传的媒体 ID（用户传入列表的最后一个），客户端据此精准跳转
        String latestMediaId = mediaIds.isEmpty() ? null : mediaIds.get(mediaIds.size() - 1);
        notifyLifeConsoleChanged(libraryId, currentUser.userId(),
                category.name().toLowerCase() + "_media_added", latestMediaId);
        // 诊断日志: life addMedia 操作后的 photoFeedVersion
        Long photoFeedVersionAfter = mediaRepository.findLatestUpdatedAtByLibraryIdAndDeletedAtIsNullAndDomainNotLife(libraryId)
                .map(Instant::toEpochMilli).orElse(0L);
        log.warn("addMedia: AFTER photoFeedVersion={} libraryId={} category={} changed={}",
                photoFeedVersionAfter, libraryId, category, photoFeedVersionAfter > photoFeedVersionBefore);
        return getToday(today.toString(), zone.getId(), currentUser);
    }

    /**
     * P1-1 改造: 解析媒体的展示时间 (毫秒)，用于 today/history 时间过滤。
     * 优先级: displayTimeMillis > capturedAtMillis > importedAtMillis > 当前时间.
     */
    private long resolveMediaDisplayTime(MediaEntity media) {
        if (media.getDisplayTimeMillis() != null && media.getDisplayTimeMillis() > 0L) {
            return media.getDisplayTimeMillis();
        }
        if (media.getCapturedAtMillis() != null && media.getCapturedAtMillis() > 0L) {
            return media.getCapturedAtMillis();
        }
        if (media.getImportedAtMillis() != null && media.getImportedAtMillis() > 0L) {
            return media.getImportedAtMillis();
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
        // 删除操作不发送推送通知（用户需求）：
        // 1. 删除时携带的 mediaId 已被软删除，客户端按 mediaId 反查 slot 会失败，
        //    fallback 到 category match 返回最新一张，造成"跳转到错误媒体"的体验。
        // 2. 删除是低频操作，对方刷新页面/widget 时自然会看到最新状态，无需即时通知。
        // notifyLifeConsoleChanged(currentUser.libraryId(), currentUser.userId(),
        //         category.name().toLowerCase() + "_media_deleted", mediaId);
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
        // 删除操作不发送推送通知（同 deleteMedia 的理由）
        // notifyLifeConsoleChanged(currentUser.libraryId(), currentUser.userId(), "bowel_deleted");

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
        // 位置更新不触发推送通知：
        // 1. 位置更新通常是异步定位补全（先无位置提交，再补全），对用户无意义
        // 2. 如果推送，会覆盖原来的"上传了人物/吃饭照片"通知（固定通知ID）
        // 3. 对方刷新页面/widget 时自然会看到最新位置
        // notifyLifeConsoleChanged(currentUser.libraryId(), currentUser.userId(), "media_location_updated");
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
        // 位置更新不触发推送通知（同 updateMediaLocation 的理由）
        // notifyLifeConsoleChanged(currentUser.libraryId(), currentUser.userId(), "bowel_location_updated");
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

    private void notifyLifeConsoleChanged(String libraryId, String actorUserId, String reason, String mediaId) {
        PushDispatchSupport.afterCommitAsync(() -> pushNotificationService.notifyLifeConsoleChanged(libraryId, actorUserId, reason, mediaId));
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
        // P1-1 改造: 直接从 media 表查询 life domain + lifeCategory + displayTime 范围内的媒体,
        // 不再依赖 album/post/post_media 三层关联.
        // 注意: smallAlbumIds 这里传 List.of() 即可, 因为 life 媒体不再属于任何 small_album.
        List<MediaEntity> allInDay = mediaRepository.findLifeMediaByCategoryAndDisplayTimeRange(
                libraryId, category.name(), dateRange.startMillis(), dateRange.endMillis());
        List<MediaDto> mediaDtos = allInDay.stream()
                .filter(media -> mediaBelongsToUser(media, ownerUserId))
                .sorted(Comparator.comparingLong(this::resolveMediaDisplayTime).reversed())
                .map(media -> contentMapper.toMediaDto(media, List.of()))
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
        // P1-1 改造: 直接查 media 表所有 life domain + lifeCategory 的媒体，按 displayTimeMillis 分组到对应日期.
        // 不再依赖 album/post/post_media 关联，也不再有 "fallback fill" 两段逻辑。
        LocalDate earliestDate = LocalDate.now(zone).minusDays(limitDays - 1L);

        // 一次性查出该 library + 该 category 的全部 life media（未删除），按 displayTimeMillis DESC 排序
        List<MediaEntity> allMedia = mediaRepository.findLifeMediaByCategory(libraryId, category.name());

        Map<LocalDate, List<HistoryMediaEntry>> selfByDay = new LinkedHashMap<>();
        Map<LocalDate, List<HistoryMediaEntry>> partnerByDay = new LinkedHashMap<>();

        for (MediaEntity media : allMedia) {
            long effectiveTimeMillis = resolveMediaDisplayTime(media);
            if (effectiveTimeMillis <= 0L) {
                continue;
            }
            LocalDate mediaDate = Instant.ofEpochMilli(effectiveTimeMillis).atZone(zone).toLocalDate();
            // 包含今天, 排除未来日期和早于 earliestDate 的日期
            if (mediaDate.isBefore(earliestDate) || mediaDate.isAfter(today)) {
                continue;
            }
            HistoryMediaEntry entry = new HistoryMediaEntry(media, effectiveTimeMillis);
            if (mediaBelongsToUser(media, users.currentUser().getId())) {
                selfByDay.computeIfAbsent(mediaDate, ignored -> new ArrayList<>()).add(entry);
            } else if (mediaBelongsToUser(media, users.partnerUserId())) {
                partnerByDay.computeIfAbsent(mediaDate, ignored -> new ArrayList<>()).add(entry);
            }
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
