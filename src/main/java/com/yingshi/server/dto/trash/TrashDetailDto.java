package com.yingshi.server.dto.trash;

public record TrashDetailDto(
        TrashItemDto item,
        boolean canRestore,
        boolean canMoveOutOfTrash,
        PendingCleanupDto pendingCleanup
) {
}
