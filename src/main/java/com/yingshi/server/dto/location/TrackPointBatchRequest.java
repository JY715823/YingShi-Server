package com.yingshi.server.dto.location;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** V52: 客户端批量上行轨迹点。 */
public record TrackPointBatchRequest(
        @NotEmpty(message = "points must not be empty.")
        @Valid
        List<TrackPointItem> points
) {
    public record TrackPointItem(
            @NotNull(message = "latitude is required.")
            @DecimalMin(value = "-90.0", message = "latitude must be >= -90.")
            @DecimalMax(value = "90.0", message = "latitude must be <= 90.")
            Double latitude,

            @NotNull(message = "longitude is required.")
            @DecimalMin(value = "-180.0", message = "longitude must be >= -180.")
            @DecimalMax(value = "180.0", message = "longitude must be <= 180.")
            Double longitude,

            Float accuracy,

            @Size(max = 32, message = "source must be at most 32 characters.")
            String source,

            @NotNull(message = "recordedAtMillis is required.")
            Long recordedAtMillis
    ) {
    }
}
