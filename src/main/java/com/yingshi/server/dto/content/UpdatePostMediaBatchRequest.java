package com.yingshi.server.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdatePostMediaBatchRequest(
    @NotEmpty(message = "removeMediaIds is required.")
    List<@NotBlank(message = "removeMediaId is required.") String> removeMediaIds
) {}
