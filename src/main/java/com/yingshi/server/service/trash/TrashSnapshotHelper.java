package com.yingshi.server.service.trash;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.TrashItemEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Handles serialization/deserialization of trash item snapshots.
 * Extracted from TrashService to separate snapshot data mapping from business logic.
 */
@Component
public class TrashSnapshotHelper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String writeSnapshot(Object snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Failed to serialize trash snapshot.");
        }
    }

    public <T> T readSnapshot(String snapshotJson, Class<T> type) {
        try {
            return objectMapper.readValue(snapshotJson, type);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Failed to read trash snapshot.");
        }
    }

    public SmallAlbumDeletedSnapshot readSmallAlbumDeletedSnapshot(TrashItemEntity item) {
        String snapshotJson = normalizeLegacySnapshotJson(item);
        try {
            Map<String, Object> snapshot = objectMapper.readValue(snapshotJson, new TypeReference<>() {
            });
            String smallAlbumId = readString(snapshot, "smallAlbumId");
            if (smallAlbumId == null || smallAlbumId.isBlank()) {
                smallAlbumId = readString(snapshot, "postId");
            }
            if (smallAlbumId == null || smallAlbumId.isBlank()) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Trash snapshot is missing small album id.");
            }
            return new SmallAlbumDeletedSnapshot(smallAlbumId);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Failed to read trash snapshot.");
        }
    }

    public MediaRemovedSnapshot readMediaRemovedSnapshot(TrashItemEntity item) {
        String snapshotJson = normalizeLegacySnapshotJson(item);
        try {
            Map<String, Object> snapshot = objectMapper.readValue(snapshotJson, new TypeReference<>() {
            });
            String smallAlbumId = readString(snapshot, "smallAlbumId");
            if (smallAlbumId == null || smallAlbumId.isBlank()) {
                smallAlbumId = readString(snapshot, "postId");
            }
            String mediaId = readString(snapshot, "mediaId");
            int sortOrder = readInt(snapshot, "sortOrder", 1);
            boolean wasCover = readBoolean(snapshot, "wasCover");
            if (smallAlbumId == null || smallAlbumId.isBlank() || mediaId == null || mediaId.isBlank()) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Trash snapshot is missing media restore fields.");
            }
            return new MediaRemovedSnapshot(smallAlbumId, mediaId, sortOrder, wasCover);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Failed to read trash snapshot.");
        }
    }

    public MediaSystemDeletedSnapshot readMediaSystemDeletedSnapshot(TrashItemEntity item) {
        return readSnapshot(normalizeLegacySnapshotJson(item), MediaSystemDeletedSnapshot.class);
    }

    public LargeAlbumDeletedSnapshot readLargeAlbumDeletedSnapshot(TrashItemEntity item) {
        return readSnapshot(normalizeLegacySnapshotJson(item), LargeAlbumDeletedSnapshot.class);
    }

    private String normalizeLegacySnapshotJson(TrashItemEntity item) {
        String snapshotJson = item.getSnapshotJson();
        if (snapshotJson == null || snapshotJson.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Trash snapshot is empty.");
        }
        if (!snapshotJson.matches("\\d+")) {
            return snapshotJson;
        }
        String smallAlbumId = item.getSourcePostId();
        if (smallAlbumId == null || smallAlbumId.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Trash snapshot is missing small album id.");
        }
        try {
            return objectMapper.writeValueAsString(Map.of("smallAlbumId", smallAlbumId));
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "Failed to normalize trash snapshot.");
        }
    }

    private String readString(Map<String, Object> snapshot, String key) {
        Object value = snapshot.get(key);
        if (value instanceof String stringValue) {
            return stringValue;
        }
        return value == null ? null : String.valueOf(value);
    }

    private int readInt(Map<String, Object> snapshot, String key, int defaultValue) {
        Object value = snapshot.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean readBoolean(Map<String, Object> snapshot, String key) {
        Object value = snapshot.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Snapshot record types
    // -----------------------------------------------------------------------

    public record SmallAlbumDeletedSnapshot(String smallAlbumId) {
    }

    public record LargeAlbumDeletedSnapshot(String albumId, List<String> smallAlbumIds, List<String> mediaIds) {
    }

    public record MediaRemovedSnapshot(String smallAlbumId, String mediaId, int sortOrder, boolean wasCover) {
    }

    public record MediaSystemDeletedSnapshot(
            String mediaId,
            List<MediaSystemRelationSnapshot> relations,
            List<String> coverPostIds
    ) {
    }

    public record MediaSystemRelationSnapshot(String postId, int sortOrder) {
    }
}
