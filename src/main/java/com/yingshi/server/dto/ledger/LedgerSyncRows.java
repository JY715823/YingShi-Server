package com.yingshi.server.dto.ledger;

/**
 * Typed row records for ledger sync input (client → server).
 * Field names match the JSON keys sent by the Android client.
 * All fields are nullable — null means "not provided" or "set to null".
 */
public final class LedgerSyncRows {

    private LedgerSyncRows() {
    }

    public record BookRow(
            String id,
            String name,
            String creatorUserId,
            String template,
            String currencyCode,
            String currencySymbol,
            Long coverColor,
            Integer sortOrder,
            Boolean isDeleted
    ) {
    }

    public record CategoryRow(
            String id,
            String bookId,
            String name,
            String iconKey,
            Long color,
            String type,
            Integer sortOrder,
            Boolean hidden
    ) {
    }

    public record AccountRow(
            String id,
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
            Integer sortOrder
    ) {
    }

    public record TransactionRow(
            String id,
            String bookId,
            String categoryId,
            String accountId,
            String toAccountId,
            Long amountCents,
            String type,
            Long occurredAtMillis,
            String remark,
            String method,
            Long deletedAtMillis
    ) {
    }

    public record BudgetRow(
            String id,
            String bookId,
            String period,
            Long startMillis,
            Long endMillis,
            Long totalAmountCents
    ) {
    }

    public record CategoryBudgetRow(
            String id,
            String budgetId,
            String categoryId,
            Long amountCents
    ) {
    }

    public record DeletedItemRow(
            String id,
            String bookId,
            String itemId,
            String type,
            String title,
            Long amountCents,
            Long deletedAtMillis,
            Long expiresAtMillis
    ) {
    }

    public record RecurringRuleRow(
            String id,
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
            Boolean enabled
    ) {
    }

    public record RecurringOccurrenceRow(
            String id,
            String ruleId,
            String transactionId,
            Long occurrenceAtMillis
    ) {
    }
}
