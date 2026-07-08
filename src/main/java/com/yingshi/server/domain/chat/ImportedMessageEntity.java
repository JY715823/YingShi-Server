package com.yingshi.server.domain.chat;

import com.yingshi.server.domain.LibraryScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "imported_messages")
public class ImportedMessageEntity extends LibraryScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private String chatId;

    @Column(name = "message_stable_key", nullable = false)
    private String messageStableKey;

    @Column(name = "source_message_id")
    private String sourceMessageId;

    @Column(name = "fallback_signature", length = 500)
    private String fallbackSignature;

    @Column(name = "ts", nullable = false)
    private Instant timestamp;

    @Column(name = "sender_stable_key")
    private String senderStableKey;

    @Column(name = "sender_display_name")
    private String senderDisplayName;

    @Column(name = "sender_uin", length = 50)
    private String senderUin;

    @Column(name = "msg_type", nullable = false, length = 20)
    private String msgType = "TEXT";

    @Column(columnDefinition = "text")
    private String text;

    @Column(columnDefinition = "text")
    private String html;

    @Column(name = "raw_content_json", columnDefinition = "text")
    private String rawContentJson;

    @Column(name = "reply_ref_message_id")
    private String replyRefMessageId;

    @Column(name = "reply_ref_sender_name")
    private String replyRefSenderName;

    @Column(name = "reply_ref_text", columnDefinition = "text")
    private String replyRefText;

    @Column(name = "json_title", length = 500)
    private String jsonTitle;

    @Column(name = "json_summary", columnDefinition = "text")
    private String jsonSummary;

    @Column(name = "call_summary", length = 500)
    private String callSummary;

    @Column(nullable = false)
    private Boolean recalled = false;

    @Column(name = "system_message", nullable = false)
    private Boolean systemMessage = false;

    @Column(name = "search_text", columnDefinition = "text")
    private String searchText;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getSourceMessageId() {
        return sourceMessageId;
    }

    public void setSourceMessageId(String sourceMessageId) {
        this.sourceMessageId = sourceMessageId;
    }

    public String getFallbackSignature() {
        return fallbackSignature;
    }

    public void setFallbackSignature(String fallbackSignature) {
        this.fallbackSignature = fallbackSignature;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getSenderStableKey() {
        return senderStableKey;
    }

    public void setSenderStableKey(String senderStableKey) {
        this.senderStableKey = senderStableKey;
    }

    public String getSenderDisplayName() {
        return senderDisplayName;
    }

    public void setSenderDisplayName(String senderDisplayName) {
        this.senderDisplayName = senderDisplayName;
    }

    public String getSenderUin() {
        return senderUin;
    }

    public void setSenderUin(String senderUin) {
        this.senderUin = senderUin;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public String getRawContentJson() {
        return rawContentJson;
    }

    public void setRawContentJson(String rawContentJson) {
        this.rawContentJson = rawContentJson;
    }

    public String getReplyRefMessageId() {
        return replyRefMessageId;
    }

    public void setReplyRefMessageId(String replyRefMessageId) {
        this.replyRefMessageId = replyRefMessageId;
    }

    public String getReplyRefSenderName() {
        return replyRefSenderName;
    }

    public void setReplyRefSenderName(String replyRefSenderName) {
        this.replyRefSenderName = replyRefSenderName;
    }

    public String getReplyRefText() {
        return replyRefText;
    }

    public void setReplyRefText(String replyRefText) {
        this.replyRefText = replyRefText;
    }

    public String getJsonTitle() {
        return jsonTitle;
    }

    public void setJsonTitle(String jsonTitle) {
        this.jsonTitle = jsonTitle;
    }

    public String getJsonSummary() {
        return jsonSummary;
    }

    public void setJsonSummary(String jsonSummary) {
        this.jsonSummary = jsonSummary;
    }

    public String getCallSummary() {
        return callSummary;
    }

    public void setCallSummary(String callSummary) {
        this.callSummary = callSummary;
    }

    public Boolean getRecalled() {
        return recalled;
    }

    public void setRecalled(Boolean recalled) {
        this.recalled = recalled;
    }

    public Boolean getSystemMessage() {
        return systemMessage;
    }

    public void setSystemMessage(Boolean systemMessage) {
        this.systemMessage = systemMessage;
    }

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }
}
