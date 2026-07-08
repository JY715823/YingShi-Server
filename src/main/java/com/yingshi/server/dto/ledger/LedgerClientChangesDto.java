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

import java.util.ArrayList;
import java.util.List;

/**
 * Typed input for client → server sync changes.
 * Uses typed records for type-safe field access.
 */
public record LedgerClientChangesDto(
        List<BookRow> books,
        List<CategoryRow> categories,
        List<AccountRow> accounts,
        List<TransactionRow> transactions,
        List<BudgetRow> budgets,
        List<CategoryBudgetRow> categoryBudgets,
        List<DeletedItemRow> deletedItems,
        List<RecurringRuleRow> recurringRules,
        List<RecurringOccurrenceRow> recurringOccurrences,
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
