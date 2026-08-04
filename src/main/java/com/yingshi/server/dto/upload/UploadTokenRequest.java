package com.yingshi.server.dto.upload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UploadTokenRequest(
        @NotBlank(message = "fileName is required.")
        @Size(max = 255, message = "fileName must be at most 255 characters.")
        String fileName,

        @NotBlank(message = "mimeType is required.")
        @Size(max = 120, message = "mimeType must be at most 120 characters.")
        String mimeType,

        @NotNull(message = "fileSizeBytes is required.")
        @Positive(message = "fileSizeBytes must be positive.")
        Long fileSizeBytes,

        @NotBlank(message = "mediaType is required.")
        String mediaType,

        @NotNull(message = "width is required.")
        @Positive(message = "width must be positive.")
        Integer width,

        @NotNull(message = "height is required.")
        @Positive(message = "height must be positive.")
        Integer height,

        Long durationMillis,

        @NotNull(message = "displayTimeMillis is required.")
        Long displayTimeMillis,

        Long capturedAtMillis,

        Long importedAtMillis,

        @Size(max = 20, message = "displayTimeSource must be at most 20 characters.")
        String displayTimeSource,

        @Size(max = 128, message = "sourceFingerprint must be at most 128 characters.")
        String sourceFingerprint,

        @Size(max = 255, message = "operationId must be at most 255 characters.")
        String operationId,

        @Size(max = 40, message = "operationType must be at most 40 characters.")
        String operationType,

        @Size(max = 255, message = "operationTitle must be at most 255 characters.")
        String operationTitle,

        @Positive(message = "operationMediaCount must be positive.")
        Integer operationMediaCount,

        @Size(max = 20, message = "domain must be at most 20 characters.")
        String domain,

        // life 模块分类：PERSON / MEAL / null（非 life 上传）
        @Size(max = 20, message = "lifeCategory must be at most 20 characters.")
        String lifeCategory,

        @Size(max = 255, message = "sourceItemId must be at most 255 characters.")
        String sourceItemId,

        // FR-18: Location tracking (optional, all nullable)
        Double latitude,
        Double longitude,

        @Size(max = 255, message = "locationLabel must be at most 255 characters.")
        String locationLabel,

        // V52: 位置来源 exif/inferred/manual
        @Size(max = 16, message = "locationSource must be at most 16 characters.")
        String locationSource,

        @Size(max = 128, message = "idempotencyKey must be at most 128 characters.")
        String idempotencyKey,

        // V49: 全部EXIF元数据（24个字段），客户端提取后发送，服务端直接存储
        java.util.Map<String, Object> exifMetadata
) {
}
