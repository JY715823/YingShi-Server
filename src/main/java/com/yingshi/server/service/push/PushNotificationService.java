package com.yingshi.server.service.push;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.PushPreferenceEntity;
import com.yingshi.server.dto.push.PushPreferenceDto;
import com.yingshi.server.dto.push.PushPreferencesResponse;
import com.yingshi.server.dto.push.UpdatePushPreferenceRequest;
import com.yingshi.server.repository.PushPreferenceRepository;
import com.yingshi.server.repository.UserRepository;
import com.yingshi.server.service.sse.SseEmitterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);
    private static final String LIFE_CONSOLE_CHANGED = "life_console.changed";
    public static final String MODULE_PHOTOS = "photos";
    public static final String MODULE_LIFE = "life";
    public static final String CATEGORY_PHOTOS_CONTENT_UPDATE = "content_update";
    public static final String CATEGORY_PHOTOS_COMMENT = "comment";
    public static final String CATEGORY_PHOTOS_DELETE = "delete";
    public static final String CATEGORY_PHOTOS_SYSTEM = "system";
    public static final String CATEGORY_LIFE_TRACE = "trace";
    public static final String CATEGORY_LIFE_LEDGER = "ledger";
    public static final String CATEGORY_LIFE_CHAT = "chat";
    public static final String CATEGORY_LIFE_SYSTEM = "system";

    private final PushPreferenceRepository pushPreferenceRepository;
    private final UserRepository userRepository;
    private final SseEmitterRegistry sseEmitterRegistry;

    public PushNotificationService(
            PushPreferenceRepository pushPreferenceRepository,
            UserRepository userRepository,
            SseEmitterRegistry sseEmitterRegistry
    ) {
        this.pushPreferenceRepository = pushPreferenceRepository;
        this.userRepository = userRepository;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @Transactional(readOnly = true)
    public PushPreferencesResponse getPreferences(AuthenticatedUser currentUser) {
        Map<String, Boolean> saved = pushPreferenceRepository.findByUserId(currentUser.userId()).stream()
                .collect(Collectors.toMap(
                        preference -> preference.getModule() + ":" + preference.getCategory(),
                        PushPreferenceEntity::getEnabled,
                        (left, right) -> right
                ));
        return new PushPreferencesResponse(defaultPreferences().stream()
                .map(preference -> new PushPreferenceDto(
                        preference.module(),
                        preference.category(),
                        saved.getOrDefault(preference.key(), preference.enabled())
                ))
                .toList());
    }

    @Transactional
    public PushPreferencesResponse updatePreference(UpdatePushPreferenceRequest request, AuthenticatedUser currentUser) {
        String module = normalizeKey(request.module());
        String category = normalizeKey(request.category());
        PushPreferenceDefaults defaults = defaultPreferences().stream()
                .filter(preference -> preference.module().equals(module) && preference.category().equals(category))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Unknown push preference category."));

        PushPreferenceEntity entity = pushPreferenceRepository
                .findByUserIdAndModuleAndCategory(currentUser.userId(), defaults.module(), defaults.category())
                .orElseGet(() -> {
                    PushPreferenceEntity created = new PushPreferenceEntity();
                    created.setId(IdGenerator.newId("push_pref"));
                    created.setLibraryId(currentUser.libraryId());
                    created.setUserId(currentUser.userId());
                    created.setModule(defaults.module());
                    created.setCategory(defaults.category());
                    return created;
                });
        entity.setEnabled(request.enabled());
        pushPreferenceRepository.save(entity);
        return getPreferences(currentUser);
    }

    @Transactional
    public void notifyLifeConsoleChanged(String libraryId, String actorUserId, String reason) {
        notifyLifeConsoleChanged(libraryId, actorUserId, reason, null);
    }

    @Transactional
    public void notifyLifeConsoleChanged(String libraryId, String actorUserId, String reason, String mediaId) {
        String category = lifeCategoryForReason(reason);
        String actorDisplayName = resolveDisplayName(actorUserId);
        Map<String, String> data = lifeConsoleChangedData(libraryId, actorUserId, reason, category, actorDisplayName, mediaId);
        // 服务端推送 body 内容日志（使用 warn 级别便于排查推送内容问题）
        log.warn("notifyLifeConsoleChanged: libraryId={}, actorUserId={}, reason={}, mediaId={}, title={}, body={}, targetRoute={}, category={}",
                libraryId, actorUserId, reason, mediaId, data.get("title"), data.get("body"), data.get("targetRoute"), category);

        // SSE 推送：排除 actor 自己的连接（partner-only 设计）。
        //
        // 关键：SSE 推送必须延迟到事务提交后执行（afterCommit）。
        // 原因：notifyLifeConsoleChanged 通常在 LifeConsoleService.addMedia 等事务内被调用，
        // 如果在事务提交前推送 SSE，接收方收到推送后立即调用 getToday() 查询数据库，
        // 此时 addMedia 事务尚未提交，接收方查不到新上传的媒体，
        // 导致 LifePushDispatchActivity.resolveMedia 按 mediaId 匹配失败，
        // fallback 到 category match 返回第一张，造成"通知跳转到错误媒体"的问题。
        sendSseAfterCommit(libraryId, actorUserId, data);
    }

    /**
     * 在事务提交后推送 SSE，确保接收方查询时能看到已提交的数据。
     * 如果没有活动事务，立即推送（兼容非事务上下文）。
     */
    private void sendSseAfterCommit(String libraryId, String actorUserId, Map<String, String> data) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sseEmitterRegistry.sendToPartners(libraryId, actorUserId, data);
                }
            });
            log.info("SSE push scheduled after commit: libraryId={}, reason={}", libraryId, data.get("reason"));
        } else {
            log.info("SSE push immediate (no active transaction): libraryId={}, reason={}", libraryId, data.get("reason"));
            sseEmitterRegistry.sendToPartners(libraryId, actorUserId, data);
        }
    }

    @Transactional
    public void notifyPhotoChanged(String libraryId, String actorUserId, String category, String title, String body, String targetRoute) {
        notifyPhotoChanged(libraryId, actorUserId, category, title, body, targetRoute, null, null);
    }

    @Transactional
    public void notifyPhotoChanged(
            String libraryId,
            String actorUserId,
            String category,
            String title,
            String body,
            String targetRoute,
            String notificationId,
            String groupId
    ) {
        String safeCategory = normalizePhotoCategory(category);
        String safeRoute = targetRoute == null || targetRoute.isBlank() ? "photos" : targetRoute.trim();
        String actorDisplayName = resolveDisplayName(actorUserId);

        Map<String, String> data = new HashMap<>();
        data.put("type", "photos.changed");
        data.put("event", "photos.changed");
        data.put("module", MODULE_PHOTOS);
        data.put("category", safeCategory);
        data.put("libraryId", libraryId);
        data.put("actorUserId", actorUserId);
        data.put("actorDisplayName", actorDisplayName);
        String name = actorDisplayName.isBlank() ? "你的伴侣" : actorDisplayName;
        String resolvedTitle = title == null || title.isBlank() ? "照片" : title.trim().replace("对方", name);
        String resolvedBody = body == null || body.isBlank() ? name + "更新了照片内容。" : body.trim().replace("对方", name);
        data.put("title", resolvedTitle);
        data.put("body", resolvedBody);
        data.put("targetRoute", safeRoute);
        data.put("occurredAtMillis", Long.toString(Instant.now().toEpochMilli()));
        if (notificationId != null && !notificationId.isBlank()) {
            data.put("notificationId", notificationId.trim());
        }
        if (groupId != null && !groupId.isBlank()) {
            data.put("groupId", groupId.trim());
        }

        // SSE 推送：排除 actor 自己的连接（partner-only 设计）。
        // 使用 sendSseAfterCommit 确保事务提交后再推送，
        // 避免接收方收到推送后查不到未提交的数据。
        sendSseAfterCommit(libraryId, actorUserId, data);
    }

    @Transactional(readOnly = true)
    public boolean isPushEnabled(String userId, String module, String category) {
        String safeModule = normalizeKey(module);
        String safeCategory = normalizeKey(category);
        return pushPreferenceRepository.findByUserIdAndModuleAndCategory(userId, safeModule, safeCategory)
                .map(PushPreferenceEntity::getEnabled)
                .orElse(defaultPreferenceEnabled(safeModule, safeCategory));
    }

    private Map<String, String> lifeConsoleChangedData(String libraryId, String actorUserId, String reason, String category, String actorDisplayName, String mediaId) {
        String safeReason = reason == null || reason.isBlank() ? "changed" : reason.trim();
        String occurredAtMillis = Long.toString(Instant.now().toEpochMilli());
        Map<String, String> data = new HashMap<>();
        data.put("type", LIFE_CONSOLE_CHANGED);
        data.put("event", LIFE_CONSOLE_CHANGED);
        data.put("module", MODULE_LIFE);
        data.put("category", category);
        data.put("libraryId", libraryId);
        data.put("actorUserId", actorUserId);
        data.put("actorDisplayName", actorDisplayName);
        data.put("reason", safeReason);
        data.put("title", lifePushTitle(safeReason));
        data.put("body", lifePushBody(safeReason, actorDisplayName));
        data.put("targetRoute", safeReason.startsWith("bowel_") ? "life:bowel" : "life:trace");
        data.put("occurredAtMillis", occurredAtMillis);
        data.put("notificationId", "life:" + safeReason + ":" + occurredAtMillis);
        data.put("groupId", "life:" + libraryId + ":" + LocalDate.now().toString());
        // 携带具体媒体 ID，客户端据此精准跳转到对应媒体的查看态
        if (mediaId != null && !mediaId.isBlank()) {
            data.put("mediaId", mediaId.trim());
        }
        return data;
    }

    private boolean defaultPreferenceEnabled(String module, String category) {
        return defaultPreferences().stream()
                .filter(preference -> preference.module().equals(module) && preference.category().equals(category))
                .findFirst()
                .map(PushPreferenceDefaults::enabled)
                .orElse(false);
    }

    private List<PushPreferenceDefaults> defaultPreferences() {
        return List.of(
                new PushPreferenceDefaults(MODULE_PHOTOS, CATEGORY_PHOTOS_CONTENT_UPDATE, true),
                new PushPreferenceDefaults(MODULE_PHOTOS, CATEGORY_PHOTOS_COMMENT, true),
                new PushPreferenceDefaults(MODULE_PHOTOS, CATEGORY_PHOTOS_DELETE, false),
                new PushPreferenceDefaults(MODULE_PHOTOS, CATEGORY_PHOTOS_SYSTEM, false),
                new PushPreferenceDefaults(MODULE_LIFE, CATEGORY_LIFE_TRACE, true),
                new PushPreferenceDefaults(MODULE_LIFE, CATEGORY_LIFE_LEDGER, false),
                new PushPreferenceDefaults(MODULE_LIFE, CATEGORY_LIFE_CHAT, false),
                new PushPreferenceDefaults(MODULE_LIFE, CATEGORY_LIFE_SYSTEM, false)
        ).stream()
                .sorted(Comparator.comparing(PushPreferenceDefaults::module).thenComparing(PushPreferenceDefaults::category))
                .toList();
    }

    private String lifeCategoryForReason(String reason) {
        String normalized = normalizeKey(reason);
        if (normalized.startsWith("ledger_")) {
            return CATEGORY_LIFE_LEDGER;
        }
        if (normalized.startsWith("chat_")) {
            return CATEGORY_LIFE_CHAT;
        }
        if (normalized.startsWith("system_")) {
            return CATEGORY_LIFE_SYSTEM;
        }
        return CATEGORY_LIFE_TRACE;
    }

    private String normalizePhotoCategory(String category) {
        String normalized = normalizeKey(category);
        return switch (normalized) {
            case CATEGORY_PHOTOS_COMMENT -> CATEGORY_PHOTOS_COMMENT;
            case CATEGORY_PHOTOS_DELETE -> CATEGORY_PHOTOS_DELETE;
            case CATEGORY_PHOTOS_SYSTEM -> CATEGORY_PHOTOS_SYSTEM;
            default -> CATEGORY_PHOTOS_CONTENT_UPDATE;
        };
    }

    // ── 推送文案 v2: 标题=模块名, 正文={name}+具体操作 ──
    private String lifePushTitle(String reason) {
        return switch (normalizeKey(reason)) {
            case "person_media_added", "person_media_deleted" -> "人物痕迹";
            case "meal_media_added", "meal_media_deleted" -> "吃饭痕迹";
            case "bowel_added", "bowel_deleted" -> "大便痕迹";
            default -> "今日痕迹";
        };
    }

    private String lifePushBody(String reason, String actorDisplayName) {
        String name = actorDisplayName == null || actorDisplayName.isBlank() ? "你的伴侣" : actorDisplayName.trim();
        return switch (normalizeKey(reason)) {
            case "person_media_added" -> name + "上传了人物照片";
            case "person_media_deleted" -> name + "删除了人物照片";
            case "meal_media_added" -> name + "上传了吃饭照片";
            case "meal_media_deleted" -> name + "删除了吃饭照片";
            // 大便通知文案统一使用 💩 emoji，避免"大便/排便"等直接文字
            case "bowel_added" -> name + "记录了一次💩";
            case "bowel_deleted" -> name + "撤回了一次💩";
            case "media_added" -> name + "上传了新的照片或视频";
            case "media_deleted" -> name + "删除了照片或视频";
            case "media_location_updated" -> name + "更新了照片位置";
            case "bowel_location_updated" -> name + "更新了💩位置";
            default -> name + "更新了生活记录";
        };
    }

    private String resolveDisplayName(String userId) {
        if (userId == null || userId.isBlank()) {
            return "";
        }
        return userRepository.findById(userId)
                .map(user -> {
                    String displayName = user.getDisplayName();
                    if (displayName != null && !displayName.isBlank()) {
                        return displayName.trim();
                    }
                    String account = user.getAccount();
                    return account != null ? account.trim() : "";
                })
                .orElse("");
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private record PushPreferenceDefaults(String module, String category, boolean enabled) {
        private String key() {
            return module + ":" + category;
        }
    }
}
