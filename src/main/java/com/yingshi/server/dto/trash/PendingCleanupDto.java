package com.yingshi.server.dto.trash;

public record PendingCleanupDto(
        String trashItemId,
        Long removedAtMillis,
        Long undoDeadlineMillis,
        TrashItemDto item
) {
}
