package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.ledger.LedgerSnapshotDto;
import com.yingshi.server.dto.ledger.UpsertLedgerSnapshotRequest;
import com.yingshi.server.service.ledger.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AuthRequired
@Tag(name = "Ledger")
@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @Operation(summary = "Get current ledger snapshot", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/snapshot")
    public ApiResponse<LedgerSnapshotDto> getSnapshot(
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), ledgerService.getSnapshot(currentUser));
    }

    @Operation(summary = "Replace current ledger snapshot", security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/snapshot")
    public ApiResponse<LedgerSnapshotDto> upsertSnapshot(
            @Valid @RequestBody UpsertLedgerSnapshotRequest requestBody,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), ledgerService.upsertSnapshot(requestBody, currentUser));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }
}
