package com.yingshi.server.dto.ledger;

import java.util.Map;

public record LedgerSnapshotDto(
        long versionMillis,
        Map<String, Object> payload
) {
}
