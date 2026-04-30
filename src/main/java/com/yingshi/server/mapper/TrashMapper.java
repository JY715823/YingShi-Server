package com.yingshi.server.mapper;

import com.yingshi.server.domain.TrashItemEntity;
import com.yingshi.server.dto.trash.PendingCleanupDto;
import com.yingshi.server.dto.trash.TrashDetailDto;
import com.yingshi.server.dto.trash.TrashItemDto;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class TrashMapper {

    public TrashItemDto toTrashItemDto(TrashItemEntity item, List<String> relatedPostIds, List<String> relatedMediaIds) {
        return new TrashItemDto(
                item.getId(),
                toItemType(item.getItemType().name()),
                toItemType(item.getState().name()),
                item.getSourcePostId(),
                item.getSourceMediaId(),
                item.getTitle(),
                item.getPreviewInfo(),
                item.getDeletedAt().toEpochMilli(),
                relatedPostIds,
                relatedMediaIds
        );
    }

    public PendingCleanupDto toPendingCleanupDto(TrashItemDto item, Instant removedAt, Instant undoDeadlineAt) {
        return new PendingCleanupDto(
                item.trashItemId(),
                removedAt == null ? null : removedAt.toEpochMilli(),
                undoDeadlineAt == null ? null : undoDeadlineAt.toEpochMilli(),
                item
        );
    }

    public TrashDetailDto toTrashDetailDto(
            TrashItemDto item,
            boolean canRestore,
            boolean canMoveOutOfTrash,
            PendingCleanupDto pendingCleanup
    ) {
        return new TrashDetailDto(item, canRestore, canMoveOutOfTrash, pendingCleanup);
    }

    private String toItemType(String enumName) {
        String lower = enumName.toLowerCase();
        String[] parts = lower.split("_");
        StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            result.append(Character.toUpperCase(parts[i].charAt(0)));
            result.append(parts[i].substring(1));
        }
        return result.toString();
    }
}
