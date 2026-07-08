package com.yingshi.server.domain.chat;

import com.yingshi.server.domain.LibraryScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "imported_message_search")
public class ImportedMessageSearchEntity extends LibraryScopedEntity {

    @Id
    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "chat_id", nullable = false)
    private String chatId;

    @Column(name = "message_stable_key", nullable = false)
    private String messageStableKey;

    @Column(name = "search_text", columnDefinition = "text")
    private String searchText;

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getMessageStableKey() {
        return messageStableKey;
    }

    public void setMessageStableKey(String messageStableKey) {
        this.messageStableKey = messageStableKey;
    }

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }
}
