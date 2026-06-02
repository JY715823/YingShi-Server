package com.yingshi.server.service.push;

import java.util.List;

public record PushDeliveryResult(
        int attempted,
        int successful,
        List<String> invalidTokens
) {

    public static PushDeliveryResult skipped() {
        return new PushDeliveryResult(0, 0, List.of());
    }
}
