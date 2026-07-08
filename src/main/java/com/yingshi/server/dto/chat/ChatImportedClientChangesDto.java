package com.yingshi.server.dto.chat;

import com.yingshi.server.dto.chat.ChatImportedSyncRows.ChatRow;
import com.yingshi.server.dto.chat.ChatImportedSyncRows.MessageRow;
import com.yingshi.server.dto.chat.ChatImportedSyncRows.MessageSearchRow;
import com.yingshi.server.dto.chat.ChatImportedSyncRows.ParticipantRow;
import com.yingshi.server.dto.chat.ChatImportedSyncRows.ResourceRow;
import com.yingshi.server.dto.ledger.DeletedRowRef;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed input for client → server sync changes.
 * Uses typed records for type-safe field access.
 */
public record ChatImportedClientChangesDto(
        List<ChatRow> chats,
        List<MessageRow> messages,
        List<ParticipantRow> participants,
        List<ResourceRow> resources,
        List<MessageSearchRow> messageSearch,
        List<DeletedRowRef> deletedRowIds
) {

    public static ChatImportedClientChangesDto empty() {
        return new ChatImportedClientChangesDto(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>()
        );
    }
}
