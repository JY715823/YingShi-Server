package com.yingshi.server.service.push;

import com.yingshi.server.domain.PushDeviceTokenEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "app.push.fcm", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopPushMessageSender implements PushMessageSender {

    private static final Logger log = LoggerFactory.getLogger(NoopPushMessageSender.class);

    @Override
    public PushDeliveryResult sendDataMessage(
            List<PushDeviceTokenEntity> targetTokens,
            Map<String, String> data
    ) {
        log.debug("FCM is not configured; skipped push to {} device(s): {}", targetTokens.size(), data);
        return PushDeliveryResult.skipped();
    }
}
