package com.yingshi.server.dto.ledger;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record UpsertLedgerSnapshotRequest(
        @NotNull(message = "payload is required.")
        Map<String, Object> payload
) {
}
