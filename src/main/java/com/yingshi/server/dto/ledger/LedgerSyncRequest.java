package com.yingshi.server.dto.ledger;

import jakarta.validation.Valid;

public record LedgerSyncRequest(
        long lastSyncVersionMillis,
        @Valid LedgerClientChangesDto changes
) {
}
