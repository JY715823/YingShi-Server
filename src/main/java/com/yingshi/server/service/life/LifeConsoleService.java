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
import com.yingshi.server.mapper.ContentMapper;
import com.yingshi.server.repository.AlbumRepository;
import com.yingshi.server.repository.BowelEventRepository;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.repository.SharedLibraryMemberRepository;
import com.yingshi.server.repository.UserRepository;
import com.yingshi.server.service.push.PushNotificationService;
import com.yingshi.server.service.push.PushDispatchSupport;
import com.yingshi.server.service.trash.TrashService;
import com.yingshi.server.dto.trash.TrashItemDto;
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
            PushNotificationService pushNotificationService
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
    public LifeConsoleTodayResponse addMedia(LifeConsoleMediaRequest request, AuthenticatedUser currentUser) {
        LifeConsoleCategory category = LifeConsoleCategory.parse(request.category());
        List<String> mediaIds = normalizedDistinctMediaIds(request.mediaIds());
        String libraryId = currentUser.libraryId();
        List<MediaEntity> mediaItems = mediaRepository.findByLibraryIdAndIdInAndDeletedAtIsNull(libraryId, mediaIds);
        if (mediaItems.size() != mediaIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.MEDIA_NOT_FOUND, "One or more mediaIds do not exist in the shared library.");
        }
        Map<String, MediaEntity> mediaById = mediaItems.stream().collect(Collectors.toMap(MediaEntity::getId, Function.identity()));

        ZoneId zone = ZoneId.of(DEFAULT_ZONE_ID);
        LocalDate today = LocalDate.now(zone);
        AlbumEntity album = ensureSystemAlbum(libraryId, category);
        PostEntity monthlySmallAlbum = ensureMonthlySmallAlbum(libraryId, album, YearMonth.from(today), zone);
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
        for (String mediaId : mediaIds) {
            MediaEntity media = mediaById.get(mediaId);
            media.setRecordOwnerUserId(currentUser.userId());
            if (media.getUploadedByUserId() == null || media.getUploadedByUserId().isBlank()) {
                media.setUploadedByUserId(currentUser.userId());
            }
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
        mediaRepository.saveAll(mediaItems);
        if (!newRelations.isEmpty()) {
            postMediaRepository.saveAll(newRelations);
            postMediaRepository.flush();
        }

        if (monthlySmallAlbum.getCoverMediaId() == null && !mediaIds.isEmpty()) {
            monthlySmallAlbum.setCoverMediaId(mediaIds.get(0));
            postRepository.save(monthlySmallAlbum);
        }
        if (album.getCoverMediaId() == null && !mediaIds.isEmpty()) {
            album.setCoverMediaId(mediaIds.get(0));
            albumRepository.save(album);
        }

        notifyLifeConsoleChanged(libraryId, currentUser.userId(),
                category.name().toLowerCase() + "_media_added");
        return getToday(today.toString(), DEFAULT_ZONE_ID, currentUser);
    }

    @Transactional
    public TrashItemDto deleteMedia(String mediaId, String rawCategory, AuthenticatedUser currentUser) {
        LifeConsoleCategory category = LifeConsoleCategory.parse(rawCategory);
        MediaEntity media = mediaRepository.findByIdAndLibraryIdAndDeletedAtIsNull(mediaId, currentUser.libraryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Media was not found."));
        if (!currentUser.userId().equals(media.getRecordOwnerUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "You can only delete media from your own frame.");
        }
        TrashItemDto trashItem = trashService.systemDeleteMediaAllowingEmptySmallAlbums(mediaId, currentUser);
        notifyLifeConsoleChanged(currentUser.libraryId(), currentUser.userId(),
                category.name().toLowerCase() + "_media_deleted");
        return trashItem;
    }

    @Transactional
    public LifeConsoleBowelMutationResponse addBowelEvent(AuthenticatedUser currentUser) {
        long nowMillis = Instant.now().toEpochMilli();
        BowelEventEntity event = new BowelEventEntity();
        event.setId(IdGenerator.newId("bowel"));
        event.setLibraryId(currentUser.libraryId());
        event.setUserId(currentUser.userId());
        event.setOccurredAtMillis(nowMillis);
        bowelEventRepository.save(event);
        notifyLifeConsoleChanged(currentUser.libraryId(), currentUser.userId(), "bowel_added");

        ZoneId zone = ZoneId.of(DEFAULT_ZONE_ID);
        LocalDate today = LocalDate.now(zone);
        LifeUsers users = resolveLifeUsers(currentUser);
        return new LifeConsoleBowelMutationResponse(
                toBowelEventDto(event),
                buildBowelSummary(dateRange(today, zone), users, currentUser.libraryId())
        );
    }

    @Transactional
    public LifeConsoleBowelMutationResponse deleteLatestBowelEvent(AuthenticatedUser currentUser) {
        ZoneId zone = ZoneId.of(DEFAULT_ZONE_ID);
        LocalDate today = LocalDate.now(zone);
        DateRange range = dateRange(today, zone);
        BowelEventEntity event = bowelEventRepository
                .findFirstByLibraryIdAndUserIdAndOccurredAtMillisGreaterThanEqualAndOccurredAtMillisLessThanOrderByOccurredAtMillisDesc(
                        currentUser.libraryId(),
                        currentUser.userId(),
                        range.startMillis(),
                        range.endMillis()
                )
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "No bowel event was found for today."));
        LifeConsoleBowelEventDto eventDto = toBowelEventDto(event);
        bowelEventRepository.delete(event);
        notifyLifeConsoleChanged(currentUser.libraryId(), currentUser.userId(), "bowel_deleted");

        LifeUsers users = resolveLifeUsers(currentUser);
        return new LifeConsoleBowelMutationResponse(
                eventDto,
                buildBowelSummary(range, users, currentUser.libraryId())
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
        AlbumEntity album = albumRepository.findByLibraryIdAndSystemKeyAndDeletedAtIsNull(libraryId, category.albumSystemKey()).orElse(null);
        if (album == null) {
            return new LifeConsoleMediaSlotDto(category.name(), ownerUserId, editable, List.of());
        }
        PostEntity monthlySmallAlbum = postRepository
                .findByLibraryIdAndAlbumIdAndSystemKeyAndDeletedAtIsNull(libraryId, album.getId(), yearMonth.toString())
                .orElse(null);
        if (monthlySmallAlbum == null) {
            return new LifeConsoleMediaSlotDto(category.name(), ownerUserId, editable, List.of());
        }

        List<PostMediaEntity> todayRelations = postMediaRepository
                .findByLibraryIdAndPostIdOrderBySortOrderAsc(libraryId, monthlySmallAlbum.getId())
                .stream()
                .filter(relation -> relation.getCreatedAt() != null)
                .filter(relation -> {
                    long createdMillis = relation.getCreatedAt().toEpochMilli();
                    return createdMillis >= dateRange.startMillis() && createdMillis < dateRange.endMillis();
                })
                .toList();
        if (todayRelations.isEmpty()) {
            return new LifeConsoleMediaSlotDto(category.name(), ownerUserId, editable, List.of());
        }

        List<String> mediaIds = todayRelations.stream().map(PostMediaEntity::getMediaId).toList();
        Map<String, MediaEntity> mediaById = mediaRepository.findByLibraryIdAndIdInAndDeletedAtIsNull(libraryId, mediaIds)
                .stream()
                .filter(media -> mediaBelongsToUser(media, ownerUserId))
                .collect(Collectors.toMap(MediaEntity::getId, Function.identity()));

        List<MediaDto> mediaDtos = new ArrayList<>();
        for (PostMediaEntity relation : todayRelations) {
            MediaEntity media = mediaById.get(relation.getMediaId());
            if (media != null && mediaBelongsToUser(media, ownerUserId)) {
                mediaDtos.add(contentMapper.toMediaDto(media, List.of(monthlySmallAlbum.getId())));
            }
        }
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
            Long latest = events.stream()
                    .map(BowelEventEntity::getOccurredAtMillis)
                    .max(Long::compareTo)
                    .orElse(null);
            summaries.add(new LifeConsoleBowelUserSummaryDto(
                    userId,
                    events.size(),
                    latest,
                    events.stream().map(BowelEventEntity::getOccurredAtMillis).toList()
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
        AlbumEntity album = albumRepository.findByLibraryIdAndSystemKeyAndDeletedAtIsNull(libraryId, category.albumSystemKey()).orElse(null);
        if (album == null) {
            return List.of();
        }
        List<PostEntity> posts = postRepository.findByLibraryIdAndAlbumIdAndDeletedAtIsNullOrderByDisplayTimeMillisDescUpdatedAtDesc(
                libraryId,
                album.getId()
        );
        if (posts.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now(zone);
        LocalDate earliestDate = LocalDate.now(zone).minusDays(limitDays - 1L);
        List<String> postIds = posts.stream().map(PostEntity::getId).toList();
        Map<String, PostEntity> postsById = posts.stream().collect(Collectors.toMap(PostEntity::getId, Function.identity()));
        List<PostMediaEntity> relations = postMediaRepository.findByLibraryIdAndPostIdIn(libraryId, postIds);
        if (relations.isEmpty()) {
            return List.of();
        }

        List<String> mediaIds = relations.stream().map(PostMediaEntity::getMediaId).distinct().toList();
        Map<String, MediaEntity> mediaById = mediaRepository.findByLibraryIdAndIdInAndDeletedAtIsNull(libraryId, mediaIds)
                .stream()
                .collect(Collectors.toMap(MediaEntity::getId, Function.identity()));

        Map<LocalDate, List<HistoryMediaEntry>> selfByDay = new LinkedHashMap<>();
        Map<LocalDate, List<HistoryMediaEntry>> partnerByDay = new LinkedHashMap<>();

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
                    if (!mediaDate.isBefore(today)) {
                        return;
                    }
                    HistoryMediaEntry entry = new HistoryMediaEntry(media, effectiveTimeMillis);
                    if (mediaBelongsToUser(media, users.currentUser().getId())) {
                        selfByDay.computeIfAbsent(mediaDate, ignored -> new ArrayList<>()).add(entry);
                    } else if (mediaBelongsToUser(media, users.partnerUserId())) {
                        partnerByDay.computeIfAbsent(mediaDate, ignored -> new ArrayList<>()).add(entry);
                    }
                });

        List<LocalDate> orderedDates = new ArrayList<>();
        orderedDates.addAll(selfByDay.keySet());
        partnerByDay.keySet().forEach(date -> {
            if (!orderedDates.contains(date)) {
                orderedDates.add(date);
            }
        });
        orderedDates.sort(Comparator.reverseOrder());

        return orderedDates.stream()
                .map(date -> new LifeConsoleHistoryDayDto(
                        date.toString(),
                        formatHistoryDate(date),
                        selfByDay.getOrDefault(date, List.of()).stream()
                                .sorted(Comparator.comparingLong(HistoryMediaEntry::effectiveTimeMillis).reversed())
                                .map(entry -> withDisplayTime(contentMapper.toMediaDto(entry.media(), List.of()), entry.effectiveTimeMillis()))
                                .toList(),
                        partnerByDay.getOrDefault(date, List.of()).stream()
                                .sorted(Comparator.comparingLong(HistoryMediaEntry::effectiveTimeMillis).reversed())
                                .map(entry -> withDisplayTime(contentMapper.toMediaDto(entry.media(), List.of()), entry.effectiveTimeMillis()))
                                .toList()
                ))
                .toList();
    }

    private long resolveHistoryTimeMillis(PostMediaEntity relation, MediaEntity media) {
        if (relation.getCreatedAt() != null) {
            return relation.getCreatedAt().toEpochMilli();
        }
        if (media.getImportedAtMillis() != null) {
            return media.getImportedAtMillis();
        }
        if (media.getCapturedAtMillis() != null) {
            return media.getCapturedAtMillis();
        }
        return media.getDisplayTimeMillis() != null ? media.getDisplayTimeMillis() : 0L;
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
                source.access()
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
                    if (!date.isBefore(today)) {
                        return null;
                    }
                    List<BowelEventEntity> events = eventsByDay.getOrDefault(date, List.of());
                    List<LifeConsoleBowelUserSummaryDto> summaries = userIds.stream()
                            .map(userId -> {
                                List<Long> times = events.stream()
                                        .filter(event -> Objects.equals(event.getUserId(), userId))
                                        .map(BowelEventEntity::getOccurredAtMillis)
                                        .sorted()
                                        .toList();
                                return new LifeConsoleBowelUserSummaryDto(
                                        userId,
                                        times.size(),
                                        times.isEmpty() ? null : times.get(times.size() - 1),
                                        times
                                );
                            })
                            .filter(summary -> summary.count() > 0)
                            .toList();
                    return new LifeConsoleBowelHistoryDayDto(
                            date.toString(),
                            formatHistoryDate(date),
                            summaries
                    );
                })
                .filter(Objects::nonNull)
                .filter(day -> !day.users().isEmpty())
                .toList();
    }

    private AlbumEntity ensureSystemAlbum(String libraryId, LifeConsoleCategory category) {
        AlbumEntity album = albumRepository.findByLibraryIdAndSystemKeyAndDeletedAtIsNull(libraryId, category.albumSystemKey()).orElse(null);
        if (album == null) {
            album = new AlbumEntity();
            album.setId(IdGenerator.newId("album"));
            album.setLibraryId(libraryId);
            album.setSystemKey(category.albumSystemKey());
            album.setTitle(category.albumTitle());
            album.setSubtitle("");
            album.setCoverMediaId(null);
        }
        album.setIncludeInPhotoFeed(category.includeInPhotoFeed());
        if (album.getTitle() == null || album.getTitle().isBlank()) {
            album.setTitle(category.albumTitle());
        }
        return albumRepository.save(album);
    }

    private PostEntity ensureMonthlySmallAlbum(String libraryId, AlbumEntity album, YearMonth yearMonth, ZoneId zone) {
        String systemKey = yearMonth.toString();
        PostEntity post = postRepository
                .findByLibraryIdAndAlbumIdAndSystemKeyAndDeletedAtIsNull(libraryId, album.getId(), systemKey)
                .orElse(null);
        if (post != null) {
            return post;
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
        return new LifeConsoleBowelEventDto(event.getId(), event.getUserId(), event.getOccurredAtMillis());
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
