package com.yingshi.server.dto.content;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdatePostMediaOrderRequest(
        @NotEmpty(message = "orderedMediaIds is required.") List<String> orderedMediaIds
) {
}
