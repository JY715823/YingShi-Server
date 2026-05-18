package com.yingshi.server.service.storage;

import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ObjectKeyPolicy {

    private static final Pattern WINDOWS_DRIVE_PATH = Pattern.compile("^[A-Za-z]:/.*");

    private ObjectKeyPolicy() {
    }

    public static String normalizeRelativeObjectKey(String objectKey) {
        String normalized = tryNormalizeRelativeObjectKey(objectKey);
        if (normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_STORAGE_ERROR, "Object key must be a relative storage key.");
        }
        return normalized;
    }

    public static String tryNormalizeRelativeObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        String normalized = objectKey.trim().replace('\\', '/');
        if (normalized.isBlank()
                || normalized.startsWith("/")
                || normalized.startsWith("//")
                || normalized.contains("//")
                || looksLikeFullUrl(normalized)
                || WINDOWS_DRIVE_PATH.matcher(normalized).matches()
                || normalized.contains("\u0000")) {
            return null;
        }
        String[] segments = normalized.split("/");
        boolean unsafeSegment = Arrays.stream(segments)
                .anyMatch(segment -> segment.isBlank() || ".".equals(segment) || "..".equals(segment));
        if (unsafeSegment) {
            return null;
        }
        return String.join("/", segments);
    }

    public static boolean isRelativeObjectKey(String objectKey) {
        return tryNormalizeRelativeObjectKey(objectKey) != null;
    }

    public static boolean looksLikeFullUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("s3://")
                || normalized.startsWith("oss://")
                || normalized.startsWith("file://")
                || normalized.startsWith("//")
                || normalized.contains("://");
    }
}
