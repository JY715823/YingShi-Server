package com.yingshi.server.dto.ledger;

public record LedgerSyncRequest(
        long lastSyncVersionMillis,
        LedgerClientChangesDto changes
) {
}
