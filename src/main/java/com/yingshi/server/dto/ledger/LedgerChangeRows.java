package com.yingshi.server.dto.ledger;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Typed row records for ledger sync output (server -> client).
 * Field names match JSON keys expected by the Android Gson deserializer.
 * All fields are nullable -- null means "not set" (excluded from JSON via @JsonInclude).
 *
 * <p>Each row adds {@code libraryId}, {@code createdAtMillis}, {@code updatedAtMillis}
 * beyond the input {@link LedgerSyncRows} counterpart. This replaces the previous
 * {@code List<Map<String,Object>>} output for a type-safe sync contract (FR-10).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class LedgerChangeRows {

    private LedgerChangeRows() {
    }

    public record BookChangeRow(
            String id,
            String libraryId,
            String name,
            String creatorUserId,
            String template,
            String currencyCode,
            String currencySymbol,
            Long coverColor,
            Integer sortOrder,
            Boolean isDeleted,
            Long createdAtMillis,
            Long updatedAtMillis
    ) {
    }

    public record CategoryChangeRow(
            String id,
            String libraryId,
            String bookId,
            String name,
            String iconKey,
            Long color,
            String type,
            Integer sortOrder,
            Boolean hidden,
            Long deletedAtMillis,
            Long createdAtMillis,
            Long updatedAtMillis
    ) {
    }

    public record AccountChangeRow(
            String id,
            String libraryId,
            String bookId,
            String name,
            String type,
            String iconKey,
            Long color,
            Long initialBalanceCents,
            Long balanceCents,
            Long creditLimitCents,
            Boolean includeInTotal,
            Boolean hidden,
            String note,
            Integer sortOrder,
            Long deletedAtMillis,
            Long createdAtMillis,
            Long updatedAtMillis
    ) {
    }

    public record TransactionChangeRow(
            String id,
            String libraryId,
            String bookId,
            String categoryId,
            String accountId,
            String toAccountId,
            Long amountCents,
            String type,
            Long occurredAtMillis,
            String remark,
            String method,
            Long deletedAtMillis,
            Long createdAtMillis,
            Long updatedAtMillis
    ) {
    }

    public record BudgetChangeRow(
            String id,
            String libraryId,
            String bookId,
            String period,
            Long startMillis,
            Long endMillis,
            Long totalAmountCents,
            Long deletedAtMillis,
            Long createdAtMillis,
            Long updatedAtMillis
    ) {
    }

    public record CategoryBudgetChangeRow(
            String id,
            String libraryId,
            String budgetId,
            String categoryId,
            Long amountCents,
            Long deletedAtMillis,
            Long createdAtMillis,
            Long updatedAtMillis
    ) {
    }

    public record DeletedItemChangeRow(
            String id,
            String libraryId,
            String bookId,
            String itemId,
            String type,
            String title,
            Long amountCents,
            Long deletedAtMillis,
            Long expiresAtMillis,
            Long createdAtMillis,
            Long updatedAtMillis
    ) {
    }

    public record RecurringRuleChangeRow(
            String id,
            String libraryId,
            String bookId,
            String type,
            String categoryId,
            String accountId,
            String toAccountId,
            Long amountCents,
            String remark,
            String frequency,
            Long startAtMillis,
            Long endAtMillis,
            Long nextOccurrenceAtMillis,
            Boolean enabled,
            Long deletedAtMillis,
            Long createdAtMillis,
            Long updatedAtMillis
    ) {
    }

    public record RecurringOccurrenceChangeRow(
            String id,
            String libraryId,
            String ruleId,
            String transactionId,
            Long occurrenceAtMillis,
            Long deletedAtMillis,
            Long createdAtMillis,
            Long updatedAtMillis
    ) {
    }
}
