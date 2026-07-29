package com.yingshi.server.dto.chat;

import java.util.List;

/**
 * Response body for the /sync endpoint.
 *
 * <p>R1-B-2: Carries the next cursor ({@code nextSyncSequence}), per-operation
 * feedback ({@code rejectedOperationIds}, {@code conflictOperationIds}) and the
 * server-side changes to apply on the client.
 */
public record ChatImportedSyncResponse(
        Long nextSyncSequence,
        List<String> rejectedOperationIds,
        List<String> conflictOperationIds,
        ChatImportedChangesDto changes
) {
}
