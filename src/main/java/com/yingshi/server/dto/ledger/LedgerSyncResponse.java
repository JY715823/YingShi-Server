package com.yingshi.server.dto.ledger;

import java.util.List;
import java.util.Map;

public record LedgerSyncResponse(
        long versionMillis,
        LedgerChangesDto changes
) {
}
