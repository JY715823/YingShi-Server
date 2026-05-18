package com.yingshi.server.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String requestId,
        T data,
        PageInfo page,
        ApiError error,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(String requestId, T data) {
        return new ApiResponse<>(requestId, data, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> success(String requestId, T data, PageInfo page) {
        return new ApiResponse<>(requestId, data, page, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String requestId, ApiError error) {
        return new ApiResponse<>(requestId, null, null, error, Instant.now());
    }
}
