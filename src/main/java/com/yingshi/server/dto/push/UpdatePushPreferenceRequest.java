package com.yingshi.server.dto.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePushPreferenceRequest(
        @NotBlank(message = "module is required.")
        @Size(max = 40, message = "module must be at most 40 characters.")
        String module,

        @NotBlank(message = "category is required.")
        @Size(max = 80, message = "category must be at most 80 characters.")
        String category,

        boolean enabled
) {
}
