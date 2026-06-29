package com.yingshi.server.service.push;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.PushDeviceTokenEntity;
import com.yingshi.server.domain.PushDeliveryAuditEntity;
import com.yingshi.server.domain.PushPreferenceEntity;
import com.yingshi.server.dto.push.PushDeliveryAuditDto;
import com.yingshi.server.dto.push.PushDiagnosticsResponse;
import com.yingshi.server.dto.life.RegisterPushTokenRequest;
import com.yingshi.server.dto.life.RegisterPushTokenResponse;
import com.yingshi.server.dto.push.PushPreferenceDto;
import com.yingshi.server.dto.push.PushPreferencesResponse;
import com.yingshi.server.dto.push.UpdatePushPreferenceRequest;
import com.yingshi.server.repository.PushDeviceTokenRepository;
import com.yingshi.server.repository.PushDeliveryAuditRepository;
import com.yingshi.server.repository.PushPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    private final PushDeviceTokenRepository pushDeviceTokenRepository;
    private final PushDeliveryAuditRepository pushDeliveryAuditRepository;
    private final PushPreferenceRepository pushPreferenceRepository;
    private final PushMessageSender pushMessageSender;
    private final boolean selfFallbackEnabled;

    public PushNotificationService(
            PushDeviceTokenRepository pushDeviceTokenRepository,
            PushDeliveryAuditRepository pushDeliveryAuditRepository,
            PushPreferenceRepository pushPreferenceRepository,
            PushMessageSender pushMessageSender,
            @Value("${app.push.self-fallback-enabled:false}") boolean selfFallbackEnabled
    ) {
        this.pushDeviceTokenRepository = pushDeviceTokenRepository;
        this.pushDeliveryAuditRepository = pushDeliveryAuditRepository;
        this.pushPreferenceRepository = pushPreferenceRepository;
        this.pushMessageSender = pushMessageSender;
        this.selfFallbackEnabled = selfFallbackEnabled;
    }

    @Transactional
    public RegisterPushTokenResponse registerDeviceToken(RegisterPushTokenRequest request, AuthenticatedUser currentUser) {
        long nowMillis = Instant.now().toEpochMilli();
        String token = request.token().trim();
        PushDeviceTokenEntity entity = pushDeviceTokenRepository.findByToken(token)
                .orElseGet(() -> {
                    PushDeviceTokenEntity created = new PushDeviceTokenEntity();
                    created.setId(IdGenerator.newId("push_token"));
                    created.setToken(token);
                    return created;
                });
        entity.setLibraryId(currentUser.libraryId());
        entity.setUserId(currentUser.userId());
        entity.setPlatform(request.platform().trim().toLowerCase());
        entity.setEnabled(true);
        entity.setLastSeenAtMillis(nowMillis);
        pushDeviceTokenRepository.save(entity);
        log.info(
                "Registered push token: tokenId={}, userId={}, libraryId={}, platform={}, tokenPrefix={}",
                entity.getId(),
                entity.getUserId(),
                entity.getLibraryId(),
                entity.getPlatform(),
                tokenPrefix(entity.getToken())
        );
        return new RegisterPushTokenResponse(entity.getId(), entity.getPlatform(), entity.getLastSeenAtMillis(), true);
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

    @Transactional(readOnly = true)
    public PushDiagnosticsResponse getDiagnostics(AuthenticatedUser currentUser) {
        return new PushDiagnosticsResponse(
                selfFallbackEnabled,
                Math.toIntExact(pushDeviceTokenRepository.countByLibraryIdAndUserIdAndEnabledTrue(currentUser.libraryId(), currentUser.userId())),
                Math.toIntExact(pushDeviceTokenRepository.countByLibraryIdAndEnabledTrue(currentUser.libraryId())),
                pushDeliveryAuditRepository.findTop30ByLibraryIdOrderByCreatedAtDesc(currentUser.libraryId()).stream()
                        .map(this::toAuditDto)
                        .toList()
        );
    }

    @Transactional
    public void notifyLifeConsoleChanged(String libraryId, String actorUserId, String reason) {
        String category = lifeCategoryForReason(reason);
        TokenResolution resolution = targetTokensFor(libraryId, actorUserId, MODULE_LIFE, category);
        if (resolution.targetTokens().isEmpty()) {
            recordAudit(
                    libraryId,
                    actorUserId,
                    MODULE_LIFE,
                    category,
                    LIFE_CONSOLE_CHANGED,
                    "life",
                    "no_target",
                    noTargetReason(resolution),
                    resolution,
                    PushDeliveryResult.skipped()
            );
            log.info("No target device token for {}: libraryId={}, actorUserId={}, reason={}, selfFallbackEnabled={}", LIFE_CONSOLE_CHANGED, libraryId, actorUserId, reason, selfFallbackEnabled);
            return;
        }

        Map<String, String> data = lifeConsoleChangedData(libraryId, actorUserId, reason, category);
        PushDeliveryResult result = pushMessageSender.sendDataMessage(resolution.targetTokens(), data);
        if (!result.invalidTokens().isEmpty()) {
            disableInvalidTokens(result.invalidTokens());
        }
        recordAudit(
                libraryId,
                actorUserId,
                MODULE_LIFE,
                category,
                LIFE_CONSOLE_CHANGED,
                data.get("targetRoute"),
                deliveryStatus(result),
                deliveryReason(result),
                resolution,
                result
        );
        log.info(
                "Sent {} to {} device(s), successful={}, invalid={}: {}",
                LIFE_CONSOLE_CHANGED,
                result.attempted(),
                result.successful(),
                result.invalidTokens().size(),
                data
        );
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
        TokenResolution resolution = targetTokensFor(libraryId, actorUserId, MODULE_PHOTOS, safeCategory);
        if (resolution.targetTokens().isEmpty()) {
            recordAudit(
                    libraryId,
                    actorUserId,
                    MODULE_PHOTOS,
                    safeCategory,
                    "photos.changed",
                    safeRoute,
                    "no_target",
                    noTargetReason(resolution),
                    resolution,
                    PushDeliveryResult.skipped()
            );
            log.info("No target device token for photo push: libraryId={}, actorUserId={}, category={}, selfFallbackEnabled={}", libraryId, actorUserId, safeCategory, selfFallbackEnabled);
            return;
        }
        Map<String, String> data = new HashMap<>();
        data.put("type", "photos.changed");
        data.put("event", "photos.changed");
        data.put("module", MODULE_PHOTOS);
        data.put("category", safeCategory);
        data.put("libraryId", libraryId);
        data.put("actorUserId", actorUserId);
        data.put("title", title == null || title.isBlank() ? "照片模块有新提醒" : title.trim());
        data.put("body", body == null || body.isBlank() ? "对方刚更新了照片内容。" : body.trim());
        data.put("targetRoute", safeRoute);
        data.put("occurredAtMillis", Long.toString(Instant.now().toEpochMilli()));
        if (notificationId != null && !notificationId.isBlank()) {
            data.put("notificationId", notificationId.trim());
        }
        if (groupId != null && !groupId.isBlank()) {
            data.put("groupId", groupId.trim());
        }
        PushDeliveryResult result = pushMessageSender.sendDataMessage(resolution.targetTokens(), data);
        if (!result.invalidTokens().isEmpty()) {
            disableInvalidTokens(result.invalidTokens());
        }
        recordAudit(
                libraryId,
                actorUserId,
                MODULE_PHOTOS,
                safeCategory,
                "photos.changed",
                safeRoute,
                deliveryStatus(result),
                deliveryReason(result),
                resolution,
                result
        );
        log.info(
                "Sent photo push to {} device(s), successful={}, invalid={}, category={}, route={}, targetUsers={}",
                result.attempted(),
                result.successful(),
                result.invalidTokens().size(),
                safeCategory,
                data.get("targetRoute"),
                resolution.targetTokens().stream().map(PushDeviceTokenEntity::getUserId).distinct().toList()
        );
    }

    @Transactional(readOnly = true)
    public boolean isPushEnabled(String userId, String module, String category) {
        String safeModule = normalizeKey(module);
        String safeCategory = normalizeKey(category);
        return pushPreferenceRepository.findByUserIdAndModuleAndCategory(userId, safeModule, safeCategory)
                .map(PushPreferenceEntity::getEnabled)
                .orElse(defaultPreferenceEnabled(safeModule, safeCategory));
    }

    private Map<String, String> lifeConsoleChangedData(String libraryId, String actorUserId, String reason, String category) {
        String safeReason = reason == null || reason.isBlank() ? "changed" : reason.trim();
        Map<String, String> data = new HashMap<>();
        data.put("type", LIFE_CONSOLE_CHANGED);
        data.put("event", LIFE_CONSOLE_CHANGED);
        data.put("module", MODULE_LIFE);
        data.put("category", category);
        data.put("libraryId", libraryId);
        data.put("actorUserId", actorUserId);
        data.put("reason", safeReason);
        data.put("title", "今日痕迹有新更新");
        data.put("body", lifePushBody(safeReason));
        data.put("targetRoute", safeReason.startsWith("bowel_") ? "life:bowel" : "life:trace");
        data.put("occurredAtMillis", Long.toString(Instant.now().toEpochMilli()));
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
                new PushPreferenceDefaults(MODULE_LIFE, CATEGORY_LIFE_TRACE, true),
                new PushPreferenceDefaults(MODULE_LIFE, CATEGORY_LIFE_LEDGER, false),
                new PushPreferenceDefaults(MODULE_LIFE, CATEGORY_LIFE_CHAT, false)
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

    private String lifePushBody(String reason) {
        return switch (normalizeKey(reason)) {
            case "media_added" -> "对方刚把新的照片或视频放进今日痕迹。";
            case "media_deleted" -> "对方整理了今日痕迹里的媒体。";
            case "bowel_added" -> "对方记录了一次排便。";
            case "bowel_deleted" -> "对方撤回了一条排便记录。";
            default -> "对方刚更新了生活记录。";
        };
    }

    private TokenResolution targetTokensFor(String libraryId, String actorUserId, String module, String category) {
        List<PushDeviceTokenEntity> enabledTokens = pushDeviceTokenRepository.findByLibraryIdAndEnabledTrue(libraryId);
        List<PushDeviceTokenEntity> partnerDeviceTokens = enabledTokens.stream()
                .filter(token -> !actorUserId.equals(token.getUserId()))
                .toList();
        List<PushDeviceTokenEntity> partnerTokens = partnerDeviceTokens.stream()
                .filter(token -> isPushEnabled(token.getUserId(), module, category))
                .toList();
        if (!partnerTokens.isEmpty() || !selfFallbackEnabled || !partnerDeviceTokens.isEmpty()) {
            return new TokenResolution(
                    partnerTokens,
                    enabledTokens.size(),
                    partnerDeviceTokens.size(),
                    partnerTokens.size(),
                    0,
                    false
            );
        }
        if (MODULE_PHOTOS.equals(module) && CATEGORY_PHOTOS_DELETE.equals(category)) {
            return new TokenResolution(
                    List.of(),
                    enabledTokens.size(),
                    partnerDeviceTokens.size(),
                    partnerTokens.size(),
                    0,
                    false
            );
        }
        List<PushDeviceTokenEntity> selfTokens = enabledTokens.stream()
                .filter(token -> actorUserId.equals(token.getUserId()))
                .filter(token -> isPushEnabled(token.getUserId(), module, category))
                .toList();
        if (!selfTokens.isEmpty()) {
            log.info(
                    "Using self push fallback for local verification: libraryId={}, actorUserId={}, module={}, category={}, devices={}",
                    libraryId,
                    actorUserId,
                    module,
                    category,
                    selfTokens.size()
            );
        }
        return new TokenResolution(
                selfTokens,
                enabledTokens.size(),
                partnerDeviceTokens.size(),
                partnerTokens.size(),
                selfTokens.size(),
                !selfTokens.isEmpty()
        );
    }

    private String noTargetReason(TokenResolution resolution) {
        if (resolution.enabledDeviceCount() == 0) {
            return "no_enabled_device_token";
        }
        if (resolution.partnerDeviceCount() == 0 && !selfFallbackEnabled) {
            return "no_partner_token_self_fallback_disabled";
        }
        if (resolution.partnerDeviceCount() == 0) {
            return "no_partner_token";
        }
        if (resolution.partnerEligibleCount() == 0) {
            return "partner_preference_disabled";
        }
        return "no_eligible_token";
    }

    private String deliveryStatus(PushDeliveryResult result) {
        if (result.successful() > 0) {
            return "sent";
        }
        if (result.attempted() == 0) {
            return "skipped";
        }
        return "failed";
    }

    private String deliveryReason(PushDeliveryResult result) {
        if (result.successful() > 0) {
            return "fcm_accepted";
        }
        if (result.attempted() == 0) {
            return "sender_skipped";
        }
        if (!result.invalidTokens().isEmpty()) {
            return "invalid_token";
        }
        return "fcm_failed";
    }

    private void recordAudit(
            String libraryId,
            String actorUserId,
            String module,
            String category,
            String eventType,
            String targetRoute,
            String status,
            String reason,
            TokenResolution resolution,
            PushDeliveryResult result
    ) {
        PushDeliveryAuditEntity audit = new PushDeliveryAuditEntity();
        audit.setId(IdGenerator.newId("push_audit"));
        audit.setLibraryId(libraryId);
        audit.setActorUserId(actorUserId);
        audit.setModule(module);
        audit.setCategory(category);
        audit.setEventType(eventType);
        audit.setTargetRoute(targetRoute == null || targetRoute.isBlank() ? "unknown" : targetRoute);
        audit.setStatus(status);
        audit.setReason(reason);
        audit.setEnabledDeviceCount(resolution.enabledDeviceCount());
        audit.setPartnerDeviceCount(resolution.partnerDeviceCount());
        audit.setTargetDeviceCount(resolution.targetTokens().size());
        audit.setAttemptedCount(result.attempted());
        audit.setSuccessfulCount(result.successful());
        audit.setInvalidTokenCount(result.invalidTokens().size());
        audit.setUsedSelfFallback(resolution.usedSelfFallback());
        pushDeliveryAuditRepository.save(audit);
    }

    private PushDeliveryAuditDto toAuditDto(PushDeliveryAuditEntity audit) {
        return new PushDeliveryAuditDto(
                audit.getId(),
                audit.getModule(),
                audit.getCategory(),
                audit.getEventType(),
                audit.getStatus(),
                audit.getReason(),
                audit.getTargetRoute(),
                audit.getActorUserId(),
                audit.getEnabledDeviceCount(),
                audit.getPartnerDeviceCount(),
                audit.getTargetDeviceCount(),
                audit.getAttemptedCount(),
                audit.getSuccessfulCount(),
                audit.getInvalidTokenCount(),
                audit.getUsedSelfFallback(),
                audit.getCreatedAt().toEpochMilli()
        );
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String tokenPrefix(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String trimmed = token.trim();
        return trimmed.substring(0, Math.min(12, trimmed.length()));
    }

    private void disableInvalidTokens(List<String> invalidTokens) {
        pushDeviceTokenRepository.findByTokenIn(invalidTokens)
                .forEach(token -> {
                    token.setEnabled(false);
                    pushDeviceTokenRepository.save(token);
                });
    }

    private record PushPreferenceDefaults(String module, String category, boolean enabled) {
        private String key() {
            return module + ":" + category;
        }
    }

    private record TokenResolution(
            List<PushDeviceTokenEntity> targetTokens,
            int enabledDeviceCount,
            int partnerDeviceCount,
            int partnerEligibleCount,
            int selfEligibleCount,
            boolean usedSelfFallback
    ) {
    }
}
