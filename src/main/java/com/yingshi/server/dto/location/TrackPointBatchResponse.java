package com.yingshi.server.dto.location;

/** V52: 批量上行结果。 */
public record TrackPointBatchResponse(
        int received,
        int inserted,
        int skipped
) {
}
