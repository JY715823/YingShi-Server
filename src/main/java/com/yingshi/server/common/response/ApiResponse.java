package com.yingshi.server.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String requestId,
        T data,
        ApiError error,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(String requestId, T data) {
        return new ApiResponse<>(requestId, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String requestId, ApiError error) {
        return new ApiResponse<>(requestId, null, error, Instant.now());
    }
}
