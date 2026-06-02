package com.yingshi.server.dto.life;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterPushTokenRequest(
        @NotBlank(message = "platform is required.")
        @Size(max = 40, message = "platform must be at most 40 characters.")
        String platform,

        @NotBlank(message = "token is required.")
        @Size(max = 512, message = "token must be at most 512 characters.")
        String token
) {
}
