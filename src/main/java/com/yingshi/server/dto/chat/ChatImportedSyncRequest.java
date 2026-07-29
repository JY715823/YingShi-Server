package com.yingshi.server.dto.chat;

/**
 * Request body for the /sync endpoint.
 *
 * <p>R1-B-2: Replaced {@code lastSyncVersionMillis} with {@code lastSyncSequence},
 * a per-row monotonically increasing sequence number (see V43 migration). Clients
 * echo back {@code nextSyncSequence} from the previous response as the cursor.
 * A {@code null} value requests a full snapshot.
 */
public record ChatImportedSyncRequest(
        Long lastSyncSequence,
        ChatImportedClientChangesDto changes
) {
}
