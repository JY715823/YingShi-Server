package com.yingshi.server.dto.location;

/** V52: 轨迹点输出。 */
public record TrackPointDto(
        String userId,
        Double latitude,
        Double longitude,
        Float accuracy,
        String source,
        Long recordedAtMillis
) {
}
