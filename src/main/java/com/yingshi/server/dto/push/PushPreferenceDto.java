package com.yingshi.server.dto.push;

public record PushPreferenceDto(
        String module,
        String category,
        boolean enabled
) {
}
