package com.yingshi.server.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.ChatSnapshotEntity;
import com.yingshi.server.dto.chat.ChatSnapshotDto;
import com.yingshi.server.dto.chat.UpsertChatSnapshotRequest;
import com.yingshi.server.repository.ChatSnapshotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ChatSnapshotService {

    private final ChatSnapshotRepository chatSnapshotRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatSnapshotService(ChatSnapshotRepository chatSnapshotRepository) {
        this.chatSnapshotRepository = chatSnapshotRepository;
    }

    @Transactional(readOnly = true)
    public ChatSnapshotDto getSnapshot(AuthenticatedUser currentUser) {
        return chatSnapshotRepository.findByLibraryId(currentUser.libraryId())
                .map(this::toDto)
                .orElseGet(() -> new ChatSnapshotDto(0L, null));
    }

    @Transactional
    public ChatSnapshotDto upsertSnapshot(
            UpsertChatSnapshotRequest request,
            AuthenticatedUser currentUser
    ) {
        ChatSnapshotEntity snapshot = chatSnapshotRepository.findByLibraryId(currentUser.libraryId())
                .orElseGet(() -> {
                    ChatSnapshotEntity created = new ChatSnapshotEntity();
                    created.setId(IdGenerator.newId("chat_snapshot"));
                    created.setLibraryId(currentUser.libraryId());
                    return created;
                });
        snapshot.setPayloadJson(writePayload(request.payload()));
        ChatSnapshotEntity saved = chatSnapshotRepository.save(snapshot);
        return toDto(saved);
    }

    private ChatSnapshotDto toDto(ChatSnapshotEntity entity) {
        return new ChatSnapshotDto(
                entity.getUpdatedAt().toEpochMilli(),
                readPayload(entity.getPayloadJson())
        );
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.SERVER_ERROR,
                    "Failed to serialize chat snapshot."
            );
        }
    }

    private Map<String, Object> readPayload(String payloadJson) {
        try {
            return objectMapper.readValue(
                    payloadJson,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.SERVER_ERROR,
                    "Failed to parse chat snapshot."
            );
        }
    }
}
