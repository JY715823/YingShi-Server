package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.ledger.LedgerSyncRequest;
import com.yingshi.server.dto.ledger.LedgerSyncResponse;
import com.yingshi.server.service.ledger.LedgerSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AuthRequired
@Tag(name = "Ledger")
@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerSyncService ledgerSyncService;

    public LedgerController(LedgerSyncService ledgerSyncService) {
        this.ledgerSyncService = ledgerSyncService;
    }

    @Operation(summary = "Incremental ledger sync", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/sync")
    public ApiResponse<LedgerSyncResponse> sync(
            @RequestBody LedgerSyncRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(requestId(httpRequest), ledgerSyncService.sync(request, currentUser));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }
}
