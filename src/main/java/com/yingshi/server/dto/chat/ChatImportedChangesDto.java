package com.yingshi.server.dto.chat;

import com.yingshi.server.dto.ledger.DeletedRowRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server → client sync changes (output).
 * Uses Map for flexible field inclusion (timestamps, metadata).
 */
public record ChatImportedChangesDto(
        List<Map<String, Object>> chats,
        List<Map<String, Object>> messages,
        List<Map<String, Object>> participants,
        List<Map<String, Object>> resources,
        List<Map<String, Object>> messageSearch,
        List<DeletedRowRef> deletedRowIds
) {

    public static ChatImportedChangesDto empty() {
        return new ChatImportedChangesDto(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>()
        );
    }
}
