package com.yingshi.server.service.upload;

import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.domain.UploadState;
import com.yingshi.server.domain.UploadTaskEntity;
import com.yingshi.server.repository.UploadTaskRepository;
import com.yingshi.server.service.storage.ObjectKeyPolicy;
import com.yingshi.server.service.storage.ObjectStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;

/**
 * FR-10: 从原 UploadService 抽取的共享工具与访问器。
 * <p>
 * 纯函数（normalize/parse/instantToMillis/storedFileName/contentType/sameContentType）以 static 方法提供，
 * 依赖 Spring Bean 的访问器（requireUploadTask/deleteTaskStorageObject）以实例方法提供，
 * 供 UploadTokenService/UploadFileService/UploadHistoryService/UploadCleanupService 共享调用，
 * 避免在 4 个拆分后的 Service 中重复定义。
 */
@Component
public class UploadSupport {

    private final UploadTaskRepository uploadTaskRepository;
    private final ObjectStorageService objectStorageService;

    public UploadSupport(
            UploadTaskRepository uploadTaskRepository,
            ObjectStorageService objectStorageService
    ) {
        this.uploadTaskRepository = uploadTaskRepository;
        this.objectStorageService = objectStorageService;
    }

    /**
     * 加载上传任务并校验归属 library。任务不存在时抛 UPLOAD_NOT_FOUND。
     */
    public UploadTaskEntity requireUploadTask(String uploadId, String libraryId) {
        return uploadTaskRepository.findByIdAndLibraryId(uploadId, libraryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.UPLOAD_NOT_FOUND, "Upload task was not found."));
    }

    /**
     * 删除对象存储中的对象，吞掉异常（用于补偿/清理路径，不影响主流程）。
     * 对 key 做相对路径归一化，null/绝对路径直接跳过。
     */
    public void deleteTaskStorageObject(String objectKey) {
        String normalized = ObjectKeyPolicy.tryNormalizeRelativeObjectKey(objectKey);
        if (normalized == null) {
            return;
        }
        try {
            objectStorageService.delete(normalized);
        } catch (Exception ignored) {
        }
    }

    // ===== 纯函数工具（不依赖任何 Spring Bean） =====

    public static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static String normalizeBounded(String value, int maxLength) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    public static String normalizeMimeType(String rawMimeType) {
        if (rawMimeType == null || rawMimeType.isBlank()) {
            return null;
        }
        return rawMimeType.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeSourceFingerprint(String rawFingerprint) {
        if (rawFingerprint == null || rawFingerprint.isBlank()) {
            return null;
        }
        return rawFingerprint.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeDisplayTimeSource(String rawSource, String fallback) {
        if (rawSource == null || rawSource.isBlank()) {
            return fallback;
        }
        String normalized = rawSource.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ORIGINAL", "IMPORTED", "MANUAL" -> normalized;
            default -> fallback;
        };
    }

    public static String normalizeOperationType(String rawOperationType) {
        String normalized = normalizeNullable(rawOperationType);
        if (normalized == null) {
            return null;
        }
        String value = normalized.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "IMPORT_TO_APP", "CREATE_POST", "ADD_TO_EXISTING_POST", "LIFE_CONSOLE" -> value;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "operationType is invalid.");
        };
    }

    public static MediaType parseMediaType(String mediaType) {
        try {
            return MediaType.valueOf(mediaType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "mediaType must be image or video.");
        }
    }

    public static UploadState parseOptionalState(String rawState) {
        String normalized = normalizeNullable(rawState);
        if (normalized == null) {
            return null;
        }
        try {
            return UploadState.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "state is invalid.");
        }
    }

    public static Long instantToMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    public static String storedFileName(String objectKey) {
        int slashIndex = objectKey.lastIndexOf('/');
        return slashIndex >= 0 ? objectKey.substring(slashIndex + 1) : objectKey;
    }

    public static String contentTypeWithoutParameters(String value) {
        String normalized = normalizeMimeType(value);
        if (normalized == null) {
            return null;
        }
        int semicolonIndex = normalized.indexOf(';');
        return semicolonIndex >= 0 ? normalized.substring(0, semicolonIndex).trim() : normalized;
    }

    public static boolean sameContentType(String first, String second) {
        String left = contentTypeWithoutParameters(first);
        String right = contentTypeWithoutParameters(second);
        return left != null && left.equals(right);
    }
}
