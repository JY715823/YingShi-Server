package com.yingshi.server.dto.push;

import java.util.List;

public record PushPreferencesResponse(
        List<PushPreferenceDto> preferences
) {
}
