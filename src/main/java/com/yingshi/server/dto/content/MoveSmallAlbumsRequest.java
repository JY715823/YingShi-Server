package com.yingshi.server.dto.content;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MoveSmallAlbumsRequest(
        @NotEmpty(message = "smallAlbumIds must not be empty")
        @Size(max = 100, message = "Cannot move more than 100 small albums at once")
        List<String> smallAlbumIds
) {
}
