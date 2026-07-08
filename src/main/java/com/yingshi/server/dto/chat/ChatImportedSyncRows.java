package com.yingshi.server.dto.chat;

/**
 * Typed row records for chat imported sync input (client → server).
 * Field names match the JSON keys sent by the Android client.
 * All fields are nullable — null means "not provided" or "set to null".
 */
public final class ChatImportedSyncRows {

    private ChatImportedSyncRows() {
    }

    public record ChatRow(
            String id,
            String chatStableKey,
            String displayName,
            String chatType,
            String peerUid,
            String selfUid,
            Integer messageCount,
            String lastMessagePreview,
            Long lastImportAtMillis,
            Integer lastInsertedCount,
            Integer lastMergedCount
    ) {
    }

    public record MessageRow(
            Long id,
            String chatId,
            String messageStableKey,
            String sourceMessageId,
            String fallbackSignature,
            Long timestampMillis,
            String senderStableKey,
            String senderDisplayName,
            String senderUin,
            String msgType,
            String text,
            String html,
            String rawContentJson,
            String replyRefMessageId,
            String replyRefSenderName,
            String replyRefText,
            String jsonTitle,
            String jsonSummary,
            String callSummary,
            Boolean recalled,
            Boolean systemMessage,
            String searchText
    ) {
    }

    public record ParticipantRow(
            Long id,
            String chatId,
            String participantStableKey,
            String uid,
            String uin,
            String displayName,
            String avatarLocalPath,
            Boolean isSelf
    ) {
    }

    public record ResourceRow(
            Long id,
            Long messageId,
            Integer ordinal,
            String resType,
            String renderKind,
            String storedFileName,
            String storedObjectKey,
            String mimeType,
            String md5,
            Integer widthPx,
            Integer heightPx,
            Integer durationSeconds,
            Long fileSizeBytes
    ) {
    }

    public record MessageSearchRow(
            Long messageId,
            String chatId,
            String messageStableKey,
            String searchText
    ) {
    }
}
