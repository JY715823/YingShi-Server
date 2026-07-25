package com.yingshi.server.dto.ledger;

import com.yingshi.server.dto.ledger.LedgerSyncRows.AccountRow;
import com.yingshi.server.dto.ledger.LedgerSyncRows.BookRow;
import com.yingshi.server.dto.ledger.LedgerSyncRows.BudgetRow;
import com.yingshi.server.dto.ledger.LedgerSyncRows.CategoryBudgetRow;
import com.yingshi.server.dto.ledger.LedgerSyncRows.CategoryRow;
import com.yingshi.server.dto.ledger.LedgerSyncRows.DeletedItemRow;
import com.yingshi.server.dto.ledger.LedgerSyncRows.RecurringOccurrenceRow;
import com.yingshi.server.dto.ledger.LedgerSyncRows.RecurringRuleRow;
import com.yingshi.server.dto.ledger.LedgerSyncRows.TransactionRow;
import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed input for client -> server sync changes.
 * Uses typed records for type-safe field access.
 *
 * <p>FR-11: {@code @Valid} on each list element cascades validation into the
 * {@link LedgerSyncRows} records' {@code @Size} constraints.</p>
 */
public record LedgerClientChangesDto(
        List<@Valid BookRow> books,
        List<@Valid CategoryRow> categories,
        List<@Valid AccountRow> accounts,
        List<@Valid TransactionRow> transactions,
        List<@Valid BudgetRow> budgets,
        List<@Valid CategoryBudgetRow> categoryBudgets,
        List<@Valid DeletedItemRow> deletedItems,
        List<@Valid RecurringRuleRow> recurringRules,
        List<@Valid RecurringOccurrenceRow> recurringOccurrences,
        List<DeletedRowRef> deletedRowIds
) {

    public static LedgerClientChangesDto empty() {
        return new LedgerClientChangesDto(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>()
        );
    }
}
