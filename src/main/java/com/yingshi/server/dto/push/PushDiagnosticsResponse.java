package com.yingshi.server.dto.push;

import java.util.List;

public record PushDiagnosticsResponse(
        int currentUserEnabledDeviceCount,
        int libraryEnabledDeviceCount,
        List<PushDeliveryAuditDto> recentDeliveries
) {
}
