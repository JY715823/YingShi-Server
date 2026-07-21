package com.yingshi.server.service.life;

import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Locale;

public enum LifeConsoleCategory {
    PERSON("life.person", "人物记录", false),
    MEAL("life.meal", "吃饭记录", false);

    private final String albumSystemKey;
    private final String albumTitle;
    private final boolean includeInPhotoFeed;

    LifeConsoleCategory(String albumSystemKey, String albumTitle, boolean includeInPhotoFeed) {
        this.albumSystemKey = albumSystemKey;
        this.albumTitle = albumTitle;
        this.includeInPhotoFeed = includeInPhotoFeed;
    }

    public String albumSystemKey() {
        return albumSystemKey;
    }

    public String albumTitle() {
        return albumTitle;
    }

    public boolean includeInPhotoFeed() {
        return includeInPhotoFeed;
    }

    public static LifeConsoleCategory parse(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "category is required.");
        }
        try {
            return LifeConsoleCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "category must be PERSON or MEAL.");
        }
    }
}
