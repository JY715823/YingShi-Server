package com.yingshi.server.dto.content;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdatePostRequest(
        @Size(min = 1, max = 120, message = "title must be between 1 and 120 characters when provided.")
        String title,

        @Size(max = 1000, message = "summary must be at most 1000 characters.")
        String summary,

        @Size(min = 1, max = 120, message = "contributorLabel must be between 1 and 120 characters when provided.")
        String contributorLabel,

        Long displayTimeMillis,

        List<String> albumIds
) {
}
