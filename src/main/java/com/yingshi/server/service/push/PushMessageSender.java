package com.yingshi.server.service.push;

import com.yingshi.server.domain.PushDeviceTokenEntity;

import java.util.List;
import java.util.Map;

public interface PushMessageSender {

    PushDeliveryResult sendDataMessage(
            List<PushDeviceTokenEntity> targetTokens,
            Map<String, String> data
    );
}
