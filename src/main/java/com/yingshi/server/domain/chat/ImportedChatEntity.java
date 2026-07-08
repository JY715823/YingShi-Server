package com.yingshi.server.domain.chat;

import com.yingshi.server.domain.LibraryScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "imported_chats")
public class ImportedChatEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(name = "chat_stable_key", nullable = false)
    private String chatStableKey;

    @Column(name = "display_name", length = 500)
    private String displayName;

    @Column(name = "chat_type", nullable = false, length = 20)
    private String chatType = "UNKNOWN";

    @Column(name = "peer_uid")
    private String peerUid;

    @Column(name = "self_uid")
    private String selfUid;

    @Column(name = "message_count", nullable = false)
    private Integer messageCount = 0;

    @Column(name = "last_message_preview", columnDefinition = "text")
    private String lastMessagePreview;

    @Column(name = "last_import_at")
    private Instant lastImportAt;

    @Column(name = "last_inserted_count")
    private Integer lastInsertedCount;

    @Column(name = "last_merged_count")
    private Integer lastMergedCount;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getChatStableKey() {
        return chatStableKey;
    }

    public void setChatStableKey(String chatStableKey) {
        this.chatStableKey = chatStableKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getChatType() {
        return chatType;
    }

    public void setChatType(String chatType) {
        this.chatType = chatType;
    }

    public String getPeerUid() {
        return peerUid;
    }

    public void setPeerUid(String peerUid) {
        this.peerUid = peerUid;
    }

    public String getSelfUid() {
        return selfUid;
    }

    public void setSelfUid(String selfUid) {
        this.selfUid = selfUid;
    }

    public Integer getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(Integer messageCount) {
        this.messageCount = messageCount;
    }

    public String getLastMessagePreview() {
        return lastMessagePreview;
    }

    public void setLastMessagePreview(String lastMessagePreview) {
        this.lastMessagePreview = lastMessagePreview;
    }

    public Instant getLastImportAt() {
        return lastImportAt;
    }

    public void setLastImportAt(Instant lastImportAt) {
        this.lastImportAt = lastImportAt;
    }

    public Integer getLastInsertedCount() {
        return lastInsertedCount;
    }

    public void setLastInsertedCount(Integer lastInsertedCount) {
        this.lastInsertedCount = lastInsertedCount;
    }

    public Integer getLastMergedCount() {
        return lastMergedCount;
    }

    public void setLastMergedCount(Integer lastMergedCount) {
        this.lastMergedCount = lastMergedCount;
    }
}
