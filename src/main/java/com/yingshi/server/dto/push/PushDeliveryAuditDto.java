package com.yingshi.server.dto.push;

public record PushDeliveryAuditDto(
        String id,
        String module,
        String category,
        String eventType,
        String status,
        String reason,
        String targetRoute,
        String actorUserId,
        int enabledDeviceCount,
        int partnerDeviceCount,
        int targetDeviceCount,
        int attemptedCount,
        int successfulCount,
        int invalidTokenCount,
        boolean usedSelfFallback,
        long createdAtMillis
) {
}
