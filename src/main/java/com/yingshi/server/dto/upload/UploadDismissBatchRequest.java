package com.yingshi.server.dto.upload;

import jakarta.validation.constraints.Size;

public record UploadDismissBatchRequest(
        @Size(max = 40, message = "state must be at most 40 characters.")
        String state,

        @Size(max = 40, message = "operationType must be at most 40 characters.")
        String operationType
) {
}
