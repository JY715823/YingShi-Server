package com.yingshi.server.dto.ledger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

/**
 * Typed row records for ledger sync input (client -> server).
 * Field names match the JSON keys sent by the Android client.
 * All fields are nullable -- null means "not provided" or "set to null".
 *
 * <p>FR-11: all String fields carry {@link Size} bounds so the controller's
 * {@code @Valid} cascade rejects oversized payloads with 400 instead of 500.</p>
 *
 * <p>每个 record 都标注 {@link JsonIgnoreProperties}(ignoreUnknown = true)，
 * 容忍客户端多出的字段（如 createdAtMillis/updatedAtMillis/ownerUserId），
 * 避免 Jackson 反序列化失败导致 500。</p>
 */
public final class LedgerSyncRows {

    private LedgerSyncRows() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookRow(
            @Size(max = 255) String id,
            @Size(max = 255) String name,
            @Size(max = 255) String creatorUserId,
            @Size(max = 255) String template,
            @Size(max = 20) String currencyCode,
            @Size(max = 20) String currencySymbol,
            Long coverColor,
            Integer sortOrder,
            Boolean isDeleted
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoryRow(
            @Size(max = 255) String id,
            @Size(max = 255) String bookId,
            @Size(max = 255) String name,
            @Size(max = 255) String iconKey,
            Long color,
            @Size(max = 30) String type,
            Integer sortOrder,
            Boolean hidden,
            Long deletedAtMillis
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccountRow(
            @Size(max = 255) String id,
            @Size(max = 255) String bookId,
            @Size(max = 255) String name,
            @Size(max = 30) String type,
            @Size(max = 255) String iconKey,
            Long color,
            Long initialBalanceCents,
            Long balanceCents,
            Long creditLimitCents,
            Boolean includeInTotal,
            Boolean hidden,
            @Size(max = 2000) String note,
            Integer sortOrder,
            Long deletedAtMillis
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransactionRow(
            @Size(max = 255) String id,
            @Size(max = 255) String bookId,
            @Size(max = 255) String categoryId,
            @Size(max = 255) String accountId,
            @Size(max = 255) String toAccountId,
            Long amountCents,
            @Size(max = 30) String type,
            Long occurredAtMillis,
            @Size(max = 2000) String remark,
            @Size(max = 30) String method,
            Long deletedAtMillis
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BudgetRow(
            @Size(max = 255) String id,
            @Size(max = 255) String bookId,
            @Size(max = 30) String period,
            Long startMillis,
            Long endMillis,
            Long totalAmountCents,
            Long deletedAtMillis
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoryBudgetRow(
            @Size(max = 255) String id,
            @Size(max = 255) String budgetId,
            @Size(max = 255) String categoryId,
            Long amountCents,
            Long deletedAtMillis
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeletedItemRow(
            @Size(max = 255) String id,
            @Size(max = 255) String bookId,
            @Size(max = 255) String itemId,
            @Size(max = 30) String type,
            @Size(max = 255) String title,
            Long amountCents,
            Long deletedAtMillis,
            Long expiresAtMillis
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecurringRuleRow(
            @Size(max = 255) String id,
            @Size(max = 255) String bookId,
            @Size(max = 30) String type,
            @Size(max = 255) String categoryId,
            @Size(max = 255) String accountId,
            @Size(max = 255) String toAccountId,
            Long amountCents,
            @Size(max = 2000) String remark,
            @Size(max = 30) String frequency,
            Long startAtMillis,
            Long endAtMillis,
            Long nextOccurrenceAtMillis,
            Boolean enabled,
            Long deletedAtMillis
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecurringOccurrenceRow(
            @Size(max = 255) String id,
            @Size(max = 255) String ruleId,
            @Size(max = 255) String transactionId,
            Long occurrenceAtMillis,
            Long deletedAtMillis
    ) {
    }
}
