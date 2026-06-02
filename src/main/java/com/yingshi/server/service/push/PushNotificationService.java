package com.yingshi.server.service.push;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.domain.PushDeviceTokenEntity;
import com.yingshi.server.dto.life.RegisterPushTokenRequest;
import com.yingshi.server.dto.life.RegisterPushTokenResponse;
import com.yingshi.server.repository.PushDeviceTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);
    private static final String LIFE_CONSOLE_CHANGED = "life_console.changed";

    private final PushDeviceTokenRepository pushDeviceTokenRepository;
    private final PushMessageSender pushMessageSender;

    public PushNotificationService(
            PushDeviceTokenRepository pushDeviceTokenRepository,
            PushMessageSender pushMessageSender
    ) {
        this.pushDeviceTokenRepository = pushDeviceTokenRepository;
        this.pushMessageSender = pushMessageSender;
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
        return new RegisterPushTokenResponse(entity.getId(), entity.getPlatform(), entity.getLastSeenAtMillis(), true);
    }

    @Transactional
    public void notifyLifeConsoleChanged(String libraryId, String actorUserId, String reason) {
        List<PushDeviceTokenEntity> targetTokens = pushDeviceTokenRepository.findByLibraryIdAndEnabledTrue(libraryId).stream()
                .filter(token -> !actorUserId.equals(token.getUserId()))
                .toList();
        if (targetTokens.isEmpty()) {
            log.debug("No target device token for {}: libraryId={}, actorUserId={}, reason={}", LIFE_CONSOLE_CHANGED, libraryId, actorUserId, reason);
            return;
        }

        Map<String, String> data = lifeConsoleChangedData(libraryId, actorUserId, reason);
        PushDeliveryResult result = pushMessageSender.sendDataMessage(targetTokens, data);
        if (!result.invalidTokens().isEmpty()) {
            disableInvalidTokens(result.invalidTokens());
        }
        log.debug(
                "Sent {} to {} device(s), successful={}, invalid={}: {}",
                LIFE_CONSOLE_CHANGED,
                result.attempted(),
                result.successful(),
                result.invalidTokens().size(),
                data
        );
    }

    private Map<String, String> lifeConsoleChangedData(String libraryId, String actorUserId, String reason) {
        Map<String, String> data = new HashMap<>();
        data.put("type", LIFE_CONSOLE_CHANGED);
        data.put("event", LIFE_CONSOLE_CHANGED);
        data.put("libraryId", libraryId);
        data.put("actorUserId", actorUserId);
        data.put("reason", reason == null || reason.isBlank() ? "changed" : reason.trim());
        data.put("occurredAtMillis", Long.toString(Instant.now().toEpochMilli()));
        return data;
    }

    private void disableInvalidTokens(List<String> invalidTokens) {
        pushDeviceTokenRepository.findByTokenIn(invalidTokens)
                .forEach(token -> {
                    token.setEnabled(false);
                    pushDeviceTokenRepository.save(token);
                });
    }
}
