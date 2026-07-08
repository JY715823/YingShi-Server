package com.yingshi.server.dto.ledger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server → client sync changes (output).
 * Uses Map for flexible field inclusion (timestamps, metadata).
 */
public record LedgerChangesDto(
        List<Map<String, Object>> books,
        List<Map<String, Object>> categories,
        List<Map<String, Object>> accounts,
        List<Map<String, Object>> transactions,
        List<Map<String, Object>> budgets,
        List<Map<String, Object>> categoryBudgets,
        List<Map<String, Object>> deletedItems,
        List<Map<String, Object>> recurringRules,
        List<Map<String, Object>> recurringOccurrences,
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
