package com.yingshi.server.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MediaImportStatusRequest(
        @NotEmpty(message = "sourceFingerprints is required.")
        @Size(max = 500, message = "sourceFingerprints must contain at most 500 items.")
        List<@NotBlank(message = "sourceFingerprint must not be blank.") String> sourceFingerprints
) {
}
