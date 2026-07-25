package com.yingshi.server.dto.ledger;

import com.yingshi.server.dto.ledger.LedgerChangeRows.AccountChangeRow;
import com.yingshi.server.dto.ledger.LedgerChangeRows.BookChangeRow;
import com.yingshi.server.dto.ledger.LedgerChangeRows.BudgetChangeRow;
import com.yingshi.server.dto.ledger.LedgerChangeRows.CategoryBudgetChangeRow;
import com.yingshi.server.dto.ledger.LedgerChangeRows.CategoryChangeRow;
import com.yingshi.server.dto.ledger.LedgerChangeRows.DeletedItemChangeRow;
import com.yingshi.server.dto.ledger.LedgerChangeRows.RecurringOccurrenceChangeRow;
import com.yingshi.server.dto.ledger.LedgerChangeRows.RecurringRuleChangeRow;
import com.yingshi.server.dto.ledger.LedgerChangeRows.TransactionChangeRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client sync changes (output).
 * Typed records (FR-10) replace the previous {@code List<Map<String,Object>>}
 * for a type-safe sync contract. Null fields are excluded from JSON via
 * {@link LedgerChangeRows}'s {@code @JsonInclude(NON_NULL)}.
 */
public record LedgerChangesDto(
        List<BookChangeRow> books,
        List<CategoryChangeRow> categories,
        List<AccountChangeRow> accounts,
        List<TransactionChangeRow> transactions,
        List<BudgetChangeRow> budgets,
        List<CategoryBudgetChangeRow> categoryBudgets,
        List<DeletedItemChangeRow> deletedItems,
        List<RecurringRuleChangeRow> recurringRules,
        List<RecurringOccurrenceChangeRow> recurringOccurrences,
        List<DeletedRowRef> deletedRowIds
) {

    public static LedgerChangesDto empty() {
        return new LedgerChangesDto(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>()
        );
    }
}
