package com.yingshi.server.service.ledger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.LedgerSnapshotEntity;
import com.yingshi.server.dto.ledger.LedgerSnapshotDto;
import com.yingshi.server.dto.ledger.UpsertLedgerSnapshotRequest;
import com.yingshi.server.repository.LedgerSnapshotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class LedgerService {

    private final LedgerSnapshotRepository ledgerSnapshotRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LedgerService(LedgerSnapshotRepository ledgerSnapshotRepository) {
        this.ledgerSnapshotRepository = ledgerSnapshotRepository;
    }

    @Transactional(readOnly = true)
    public LedgerSnapshotDto getSnapshot(AuthenticatedUser currentUser) {
        return ledgerSnapshotRepository.findByLibraryId(currentUser.libraryId())
                .map(this::toDto)
                .orElseGet(() -> new LedgerSnapshotDto(0L, null));
    }

    @Transactional
    public LedgerSnapshotDto upsertSnapshot(
            UpsertLedgerSnapshotRequest request,
            AuthenticatedUser currentUser
    ) {
        LedgerSnapshotEntity snapshot = ledgerSnapshotRepository.findByLibraryId(currentUser.libraryId())
                .orElseGet(() -> {
                    LedgerSnapshotEntity created = new LedgerSnapshotEntity();
                    created.setId(IdGenerator.newId("ledger_snapshot"));
                    created.setLibraryId(currentUser.libraryId());
                    return created;
                });
        snapshot.setPayloadJson(writePayload(request.payload()));
        LedgerSnapshotEntity saved = ledgerSnapshotRepository.save(snapshot);
        return toDto(saved);
    }

    private LedgerSnapshotDto toDto(LedgerSnapshotEntity entity) {
        return new LedgerSnapshotDto(
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
                    "Failed to serialize ledger snapshot."
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
                    "Failed to parse ledger snapshot."
            );
        }
    }
}
