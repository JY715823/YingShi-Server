package com.yingshi.server.service.chat;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.domain.chat.ImportedChatEntity;
import com.yingshi.server.domain.chat.ImportedMessageEntity;
import com.yingshi.server.domain.chat.ImportedMessageSearchEntity;
import com.yingshi.server.domain.chat.ImportedParticipantEntity;
import com.yingshi.server.domain.chat.ImportedResourceEntity;
import com.yingshi.server.dto.chat.ChatImportedChangesDto;
import com.yingshi.server.dto.chat.ChatImportedClientChangesDto;
import com.yingshi.server.dto.chat.ChatImportedSyncRequest;
import com.yingshi.server.dto.chat.ChatImportedSyncResponse;
import com.yingshi.server.dto.chat.ChatImportedSyncRows.ChatRow;
import com.yingshi.server.dto.chat.ChatImportedSyncRows.MessageRow;
import com.yingshi.server.dto.chat.ChatImportedSyncRows.MessageSearchRow;
import com.yingshi.server.dto.chat.ChatImportedSyncRows.ParticipantRow;
import com.yingshi.server.dto.chat.ChatImportedSyncRows.ResourceRow;
import com.yingshi.server.dto.ledger.DeletedRowRef;
import com.yingshi.server.repository.chat.ImportedChatRepository;
import com.yingshi.server.repository.chat.ImportedMessageRepository;
import com.yingshi.server.repository.chat.ImportedMessageSearchRepository;
import com.yingshi.server.repository.chat.ImportedParticipantRepository;
import com.yingshi.server.repository.chat.ImportedResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatImportedSyncService {

    private static final Logger log = LoggerFactory.getLogger(ChatImportedSyncService.class);

    private static final String TABLE_CHAT = "imported_chats";
    private static final String TABLE_MESSAGE = "imported_messages";
    private static final String TABLE_PARTICIPANT = "imported_participants";
    private static final String TABLE_RESOURCE = "imported_resources";
    private static final String TABLE_MESSAGE_SEARCH = "imported_message_search";

    private final ImportedChatRepository chatRepository;
    private final ImportedMessageRepository messageRepository;
    private final ImportedParticipantRepository participantRepository;
    private final ImportedResourceRepository resourceRepository;
    private final ImportedMessageSearchRepository messageSearchRepository;

    public ChatImportedSyncService(
            ImportedChatRepository chatRepository,
            ImportedMessageRepository messageRepository,
            ImportedParticipantRepository participantRepository,
            ImportedResourceRepository resourceRepository,
            ImportedMessageSearchRepository messageSearchRepository
    ) {
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
        this.participantRepository = participantRepository;
        this.resourceRepository = resourceRepository;
        this.messageSearchRepository = messageSearchRepository;
    }

    @Transactional
    public ChatImportedSyncResponse sync(ChatImportedSyncRequest request, String libraryId) {
        Instant syncStart = Instant.now();

        ChatImportedClientChangesDto clientChanges = request.changes();
        if (clientChanges == null) {
            clientChanges = ChatImportedClientChangesDto.empty();
        }

        applyChanges(libraryId, clientChanges);

        if (clientChanges.deletedRowIds() != null) {
            applyDeletions(libraryId, clientChanges.deletedRowIds());
        }

        Instant since = Instant.ofEpochMilli(request.lastSyncVersionMillis());
        ChatImportedChangesDto serverChanges = queryChangesSince(libraryId, since);

        return new ChatImportedSyncResponse(syncStart.toEpochMilli(), serverChanges);
    }

    // -----------------------------------------------------------------------
    // Apply client changes (upsert typed rows)
    // -----------------------------------------------------------------------

    private void applyChanges(String libraryId, ChatImportedClientChangesDto changes) {
        upsertChats(libraryId, changes.chats());
        upsertMessages(libraryId, changes.messages());
        upsertParticipants(libraryId, changes.participants());
        upsertResources(libraryId, changes.resources());
        upsertMessageSearch(libraryId, changes.messageSearch());
    }

    private void upsertChats(String libraryId, List<ChatRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (ChatRow row : rows) {
            ImportedChatEntity entity = chatRepository.findByIdAndLibraryId(row.id(), libraryId)
                    .orElseGet(() -> {
                        ImportedChatEntity e = new ImportedChatEntity();
                        e.setId(row.id() != null ? row.id() : IdGenerator.newId("ichat"));
                        e.setLibraryId(libraryId);
                        return e;
                    });
            mapToChat(row, entity);
            chatRepository.save(entity);
        }
    }

    private void upsertMessages(String libraryId, List<MessageRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (MessageRow row : rows) {
            ImportedMessageEntity entity;
            if (row.id() != null) {
                entity = messageRepository.findByIdAndLibraryId(row.id(), libraryId)
                        .orElseGet(() -> newMessageEntity(libraryId));
            } else {
                entity = newMessageEntity(libraryId);
            }
            mapToMessage(row, entity);
            messageRepository.save(entity);
        }
    }

    private ImportedMessageEntity newMessageEntity(String libraryId) {
        ImportedMessageEntity e = new ImportedMessageEntity();
        e.setLibraryId(libraryId);
        return e;
    }

    private void upsertParticipants(String libraryId, List<ParticipantRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (ParticipantRow row : rows) {
            ImportedParticipantEntity entity;
            if (row.id() != null) {
                entity = participantRepository.findByIdAndLibraryId(row.id(), libraryId)
                        .orElseGet(() -> newParticipantEntity(libraryId));
            } else {
                entity = newParticipantEntity(libraryId);
            }
            mapToParticipant(row, entity);
            participantRepository.save(entity);
        }
    }

    private ImportedParticipantEntity newParticipantEntity(String libraryId) {
        ImportedParticipantEntity e = new ImportedParticipantEntity();
        e.setLibraryId(libraryId);
        return e;
    }

    private void upsertResources(String libraryId, List<ResourceRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (ResourceRow row : rows) {
            ImportedResourceEntity entity;
            if (row.id() != null) {
                entity = resourceRepository.findByIdAndLibraryId(row.id(), libraryId)
                        .orElseGet(() -> newResourceEntity(libraryId));
            } else {
                entity = newResourceEntity(libraryId);
            }
            mapToResource(row, entity);
            resourceRepository.save(entity);
        }
    }

    private ImportedResourceEntity newResourceEntity(String libraryId) {
        ImportedResourceEntity e = new ImportedResourceEntity();
        e.setLibraryId(libraryId);
        return e;
    }

    private void upsertMessageSearch(String libraryId, List<MessageSearchRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (MessageSearchRow row : rows) {
            ImportedMessageSearchEntity entity;
            if (row.messageId() != null) {
                entity = messageSearchRepository.findByMessageIdAndLibraryId(row.messageId(), libraryId)
                        .orElseGet(() -> newMessageSearchEntity(libraryId));
            } else {
                entity = newMessageSearchEntity(libraryId);
            }
            mapToMessageSearch(row, entity);
            messageSearchRepository.save(entity);
        }
    }

    private ImportedMessageSearchEntity newMessageSearchEntity(String libraryId) {
        ImportedMessageSearchEntity e = new ImportedMessageSearchEntity();
        e.setLibraryId(libraryId);
        return e;
    }

    // -----------------------------------------------------------------------
    // Apply deletions
    // -----------------------------------------------------------------------

    private interface BulkDeleter {
        void delete(String libraryId, List<String> ids);
    }

    private interface BulkDeleterLong {
        void delete(String libraryId, List<Long> ids);
    }

    private void applyDeletions(String libraryId, List<DeletedRowRef> deletedRowIds) {
        if (deletedRowIds == null || deletedRowIds.isEmpty()) return;

        Map<String, List<String>> idsByTable = new HashMap<>();
        for (DeletedRowRef ref : deletedRowIds) {
            idsByTable.computeIfAbsent(ref.table(), k -> new ArrayList<>()).add(ref.id());
        }

        deleteIfPresent(libraryId, idsByTable.get(TABLE_CHAT), chatRepository::deleteByLibraryIdAndIdIn);
        deleteLongIfPresent(libraryId, idsByTable.get(TABLE_MESSAGE), messageRepository::deleteByLibraryIdAndIdIn);
        deleteLongIfPresent(libraryId, idsByTable.get(TABLE_PARTICIPANT), participantRepository::deleteByLibraryIdAndIdIn);
        deleteLongIfPresent(libraryId, idsByTable.get(TABLE_RESOURCE), resourceRepository::deleteByLibraryIdAndIdIn);
        deleteLongIfPresent(libraryId, idsByTable.get(TABLE_MESSAGE_SEARCH), messageSearchRepository::deleteByLibraryIdAndMessageIdIn);
    }

    private void deleteIfPresent(String libraryId, List<String> ids, BulkDeleter deleter) {
        if (ids != null && !ids.isEmpty()) {
            deleter.delete(libraryId, ids);
        }
    }

    private void deleteLongIfPresent(String libraryId, List<String> stringIds, BulkDeleterLong deleter) {
        if (stringIds == null || stringIds.isEmpty()) return;
        List<Long> ids = new ArrayList<>();
        for (String s : stringIds) {
            try {
                ids.add(Long.parseLong(s));
            } catch (NumberFormatException e) {
                log.warn("Skipping non-numeric id '{}' for long-id table deletion", s);
            }
        }
        if (!ids.isEmpty()) {
            deleter.delete(libraryId, ids);
        }
    }

    // -----------------------------------------------------------------------
    // Query server changes since a given instant (output as Map for flexibility)
    // -----------------------------------------------------------------------

    private ChatImportedChangesDto queryChangesSince(String libraryId, Instant since) {
        List<ImportedChatEntity> chats = chatRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        List<ImportedMessageEntity> messages = messageRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        List<ImportedParticipantEntity> participants = participantRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        List<ImportedResourceEntity> resources = resourceRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        List<ImportedMessageSearchEntity> messageSearch = messageSearchRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);

        return new ChatImportedChangesDto(
                chats.stream().map(this::chatToMap).toList(),
                messages.stream().map(this::messageToMap).toList(),
                participants.stream().map(this::participantToMap).toList(),
                resources.stream().map(this::resourceToMap).toList(),
                messageSearch.stream().map(this::messageSearchToMap).toList(),
                new ArrayList<>()
        );
    }

    // -----------------------------------------------------------------------
    // Typed Row -> Entity mappers (input)
    // -----------------------------------------------------------------------

    private void mapToChat(ChatRow row, ImportedChatEntity entity) {
        entity.setChatStableKey(row.chatStableKey());
        entity.setDisplayName(row.displayName());
        entity.setChatType(row.chatType());
        entity.setPeerUid(row.peerUid());
        entity.setSelfUid(row.selfUid());
        entity.setMessageCount(row.messageCount());
        entity.setLastMessagePreview(row.lastMessagePreview());
        entity.setLastImportAt(row.lastImportAtMillis() != null ? Instant.ofEpochMilli(row.lastImportAtMillis()) : null);
        entity.setLastInsertedCount(row.lastInsertedCount());
        entity.setLastMergedCount(row.lastMergedCount());
    }

    private void mapToMessage(MessageRow row, ImportedMessageEntity entity) {
        entity.setChatId(row.chatId());
        entity.setMessageStableKey(row.messageStableKey());
        entity.setSourceMessageId(row.sourceMessageId());
        entity.setFallbackSignature(row.fallbackSignature());
        entity.setTimestamp(row.timestampMillis() != null ? Instant.ofEpochMilli(row.timestampMillis()) : null);
        entity.setSenderStableKey(row.senderStableKey());
        entity.setSenderDisplayName(row.senderDisplayName());
        entity.setSenderUin(row.senderUin());
        entity.setMsgType(row.msgType());
        entity.setText(row.text());
        entity.setHtml(row.html());
        entity.setRawContentJson(row.rawContentJson());
        entity.setReplyRefMessageId(row.replyRefMessageId());
        entity.setReplyRefSenderName(row.replyRefSenderName());
        entity.setReplyRefText(row.replyRefText());
        entity.setJsonTitle(row.jsonTitle());
        entity.setJsonSummary(row.jsonSummary());
        entity.setCallSummary(row.callSummary());
        entity.setRecalled(row.recalled() != null ? row.recalled() : false);
        entity.setSystemMessage(row.systemMessage() != null ? row.systemMessage() : false);
        entity.setSearchText(row.searchText());
    }

    private void mapToParticipant(ParticipantRow row, ImportedParticipantEntity entity) {
        entity.setChatId(row.chatId());
        entity.setParticipantStableKey(row.participantStableKey());
        entity.setUid(row.uid());
        entity.setUin(row.uin());
        entity.setDisplayName(row.displayName());
        entity.setAvatarLocalPath(row.avatarLocalPath());
        entity.setIsSelf(row.isSelf() != null ? row.isSelf() : false);
    }

    private void mapToResource(ResourceRow row, ImportedResourceEntity entity) {
        entity.setMessageId(row.messageId());
        entity.setOrdinal(row.ordinal());
        entity.setResType(row.resType());
        entity.setRenderKind(row.renderKind());
        entity.setStoredFileName(row.storedFileName());
        entity.setStoredObjectKey(row.storedObjectKey());
        entity.setMimeType(row.mimeType());
        entity.setMd5(row.md5());
        entity.setWidthPx(row.widthPx());
        entity.setHeightPx(row.heightPx());
        entity.setDurationSeconds(row.durationSeconds());
        entity.setFileSizeBytes(row.fileSizeBytes());
    }

    private void mapToMessageSearch(MessageSearchRow row, ImportedMessageSearchEntity entity) {
        entity.setMessageId(row.messageId());
        entity.setChatId(row.chatId());
        entity.setMessageStableKey(row.messageStableKey());
        entity.setSearchText(row.searchText());
    }

    // -----------------------------------------------------------------------
    // Entity -> Map mappers (output, preserves timestamps & metadata)
    // -----------------------------------------------------------------------

    private Map<String, Object> chatToMap(ImportedChatEntity e) {
        Map<String, Object> map = new HashMap<>();
        putIfNotNull(map, "id", e.getId());
        putIfNotNull(map, "libraryId", e.getLibraryId());
        putIfNotNull(map, "chatStableKey", e.getChatStableKey());
        putIfNotNull(map, "displayName", e.getDisplayName());
        putIfNotNull(map, "chatType", e.getChatType());
        putIfNotNull(map, "peerUid", e.getPeerUid());
        putIfNotNull(map, "selfUid", e.getSelfUid());
        putIfNotNull(map, "messageCount", e.getMessageCount());
        putIfNotNull(map, "lastMessagePreview", e.getLastMessagePreview());
        putIfNotNull(map, "lastImportAtMillis", instantToMillis(e.getLastImportAt()));
        putIfNotNull(map, "lastInsertedCount", e.getLastInsertedCount());
        putIfNotNull(map, "lastMergedCount", e.getLastMergedCount());
        putIfNotNull(map, "createdAtMillis", instantToMillis(e.getCreatedAt()));
        putIfNotNull(map, "updatedAtMillis", instantToMillis(e.getUpdatedAt()));
        return map;
    }

    private Map<String, Object> messageToMap(ImportedMessageEntity e) {
        Map<String, Object> map = new HashMap<>();
        putIfNotNull(map, "id", e.getId());
        putIfNotNull(map, "libraryId", e.getLibraryId());
        putIfNotNull(map, "chatId", e.getChatId());
        putIfNotNull(map, "messageStableKey", e.getMessageStableKey());
        putIfNotNull(map, "sourceMessageId", e.getSourceMessageId());
        putIfNotNull(map, "fallbackSignature", e.getFallbackSignature());
        putIfNotNull(map, "timestampMillis", instantToMillis(e.getTimestamp()));
        putIfNotNull(map, "senderStableKey", e.getSenderStableKey());
        putIfNotNull(map, "senderDisplayName", e.getSenderDisplayName());
        putIfNotNull(map, "senderUin", e.getSenderUin());
        putIfNotNull(map, "msgType", e.getMsgType());
        putIfNotNull(map, "text", e.getText());
        putIfNotNull(map, "html", e.getHtml());
        putIfNotNull(map, "rawContentJson", e.getRawContentJson());
        putIfNotNull(map, "replyRefMessageId", e.getReplyRefMessageId());
        putIfNotNull(map, "replyRefSenderName", e.getReplyRefSenderName());
        putIfNotNull(map, "replyRefText", e.getReplyRefText());
        putIfNotNull(map, "jsonTitle", e.getJsonTitle());
        putIfNotNull(map, "jsonSummary", e.getJsonSummary());
        putIfNotNull(map, "callSummary", e.getCallSummary());
        putIfNotNull(map, "recalled", e.getRecalled());
        putIfNotNull(map, "systemMessage", e.getSystemMessage());
        putIfNotNull(map, "searchText", e.getSearchText());
        putIfNotNull(map, "createdAtMillis", instantToMillis(e.getCreatedAt()));
        putIfNotNull(map, "updatedAtMillis", instantToMillis(e.getUpdatedAt()));
        return map;
    }

    private Map<String, Object> participantToMap(ImportedParticipantEntity e) {
        Map<String, Object> map = new HashMap<>();
        putIfNotNull(map, "id", e.getId());
        putIfNotNull(map, "libraryId", e.getLibraryId());
        putIfNotNull(map, "chatId", e.getChatId());
        putIfNotNull(map, "participantStableKey", e.getParticipantStableKey());
        putIfNotNull(map, "uid", e.getUid());
        putIfNotNull(map, "uin", e.getUin());
        putIfNotNull(map, "displayName", e.getDisplayName());
        putIfNotNull(map, "avatarLocalPath", e.getAvatarLocalPath());
        putIfNotNull(map, "isSelf", e.getIsSelf());
        putIfNotNull(map, "createdAtMillis", instantToMillis(e.getCreatedAt()));
        putIfNotNull(map, "updatedAtMillis", instantToMillis(e.getUpdatedAt()));
        return map;
    }

    private Map<String, Object> resourceToMap(ImportedResourceEntity e) {
        Map<String, Object> map = new HashMap<>();
        putIfNotNull(map, "id", e.getId());
        putIfNotNull(map, "libraryId", e.getLibraryId());
        putIfNotNull(map, "messageId", e.getMessageId());
        putIfNotNull(map, "ordinal", e.getOrdinal());
        putIfNotNull(map, "resType", e.getResType());
        putIfNotNull(map, "renderKind", e.getRenderKind());
        putIfNotNull(map, "storedFileName", e.getStoredFileName());
        putIfNotNull(map, "storedObjectKey", e.getStoredObjectKey());
        putIfNotNull(map, "mimeType", e.getMimeType());
        putIfNotNull(map, "md5", e.getMd5());
        putIfNotNull(map, "widthPx", e.getWidthPx());
        putIfNotNull(map, "heightPx", e.getHeightPx());
        putIfNotNull(map, "durationSeconds", e.getDurationSeconds());
        putIfNotNull(map, "fileSizeBytes", e.getFileSizeBytes());
        putIfNotNull(map, "createdAtMillis", instantToMillis(e.getCreatedAt()));
        putIfNotNull(map, "updatedAtMillis", instantToMillis(e.getUpdatedAt()));
        return map;
    }

    private Map<String, Object> messageSearchToMap(ImportedMessageSearchEntity e) {
        Map<String, Object> map = new HashMap<>();
        putIfNotNull(map, "messageId", e.getMessageId());
        putIfNotNull(map, "libraryId", e.getLibraryId());
        putIfNotNull(map, "chatId", e.getChatId());
        putIfNotNull(map, "messageStableKey", e.getMessageStableKey());
        putIfNotNull(map, "searchText", e.getSearchText());
        putIfNotNull(map, "createdAtMillis", instantToMillis(e.getCreatedAt()));
        putIfNotNull(map, "updatedAtMillis", instantToMillis(e.getUpdatedAt()));
        return map;
    }

    // -----------------------------------------------------------------------
    // Generic helpers
    // -----------------------------------------------------------------------

    private Long instantToMillis(Instant instant) {
        return instant != null ? instant.toEpochMilli() : null;
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
