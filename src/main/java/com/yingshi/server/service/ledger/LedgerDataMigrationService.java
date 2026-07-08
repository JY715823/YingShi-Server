package com.yingshi.server.service.ledger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.domain.LedgerSnapshotEntity;
import com.yingshi.server.domain.ledger.LedgerAccountEntity;
import com.yingshi.server.domain.ledger.LedgerAccountType;
import com.yingshi.server.domain.ledger.LedgerBookEntity;
import com.yingshi.server.domain.ledger.LedgerBudgetEntity;
import com.yingshi.server.domain.ledger.LedgerBudgetPeriod;
import com.yingshi.server.domain.ledger.LedgerCategoryBudgetEntity;
import com.yingshi.server.domain.ledger.LedgerCategoryEntity;
import com.yingshi.server.domain.ledger.LedgerCategoryType;
import com.yingshi.server.domain.ledger.LedgerDeletedItemEntity;
import com.yingshi.server.domain.ledger.LedgerDeletedItemType;
import com.yingshi.server.domain.ledger.LedgerRecurringFrequency;
import com.yingshi.server.domain.ledger.LedgerRecurringOccurrenceEntity;
import com.yingshi.server.domain.ledger.LedgerRecurringRuleEntity;
import com.yingshi.server.domain.ledger.LedgerTransactionEntity;
import com.yingshi.server.domain.ledger.LedgerTransactionType;
import com.yingshi.server.repository.LedgerSnapshotRepository;
import com.yingshi.server.repository.ledger.LedgerAccountRepository;
import com.yingshi.server.repository.ledger.LedgerBookRepository;
import com.yingshi.server.repository.ledger.LedgerBudgetRepository;
import com.yingshi.server.repository.ledger.LedgerCategoryBudgetRepository;
import com.yingshi.server.repository.ledger.LedgerCategoryRepository;
import com.yingshi.server.repository.ledger.LedgerDeletedItemRepository;
import com.yingshi.server.repository.ledger.LedgerRecurringOccurrenceRepository;
import com.yingshi.server.repository.ledger.LedgerRecurringRuleRepository;
import com.yingshi.server.repository.ledger.LedgerTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class LedgerDataMigrationService {

    private static final Logger log = LoggerFactory.getLogger(LedgerDataMigrationService.class);

    private final LedgerSnapshotRepository snapshotRepository;
    private final LedgerBookRepository bookRepository;
    private final LedgerCategoryRepository categoryRepository;
    private final LedgerAccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerBudgetRepository budgetRepository;
    private final LedgerCategoryBudgetRepository categoryBudgetRepository;
    private final LedgerDeletedItemRepository deletedItemRepository;
    private final LedgerRecurringRuleRepository recurringRuleRepository;
    private final LedgerRecurringOccurrenceRepository recurringOccurrenceRepository;
    private final ObjectMapper objectMapper;

    public LedgerDataMigrationService(
            LedgerSnapshotRepository snapshotRepository,
            LedgerBookRepository bookRepository,
            LedgerCategoryRepository categoryRepository,
            LedgerAccountRepository accountRepository,
            LedgerTransactionRepository transactionRepository,
            LedgerBudgetRepository budgetRepository,
            LedgerCategoryBudgetRepository categoryBudgetRepository,
            LedgerDeletedItemRepository deletedItemRepository,
            LedgerRecurringRuleRepository recurringRuleRepository,
            LedgerRecurringOccurrenceRepository recurringOccurrenceRepository
    ) {
        this.snapshotRepository = snapshotRepository;
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.categoryBudgetRepository = categoryBudgetRepository;
        this.deletedItemRepository = deletedItemRepository;
        this.recurringRuleRepository = recurringRuleRepository;
        this.recurringOccurrenceRepository = recurringOccurrenceRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public void migrateIfNeeded() {
        long existingBooks = bookRepository.count();
        if (existingBooks > 0) {
            log.info("Ledger relational tables already contain data ({} books). Skipping migration.", existingBooks);
            return;
        }

        List<LedgerSnapshotEntity> snapshots = snapshotRepository.findAll();
        if (snapshots.isEmpty()) {
            log.info("No ledger snapshots found. Nothing to migrate.");
            return;
        }

        log.info("Starting migration of {} ledger snapshot(s) to relational tables.", snapshots.size());
        int migratedCount = 0;

        for (LedgerSnapshotEntity snapshot : snapshots) {
            String libraryId = snapshot.getLibraryId();
            Map<String, Object> payload = parsePayload(snapshot.getPayloadJson());
            if (payload == null || payload.isEmpty()) {
                log.warn("Empty payload for snapshot {} (library {}). Skipping.", snapshot.getId(), libraryId);
                continue;
            }

            migrateBooks(libraryId, payload);
            migrateCategories(libraryId, payload);
            migrateAccounts(libraryId, payload);
            migrateTransactions(libraryId, payload);
            migrateBudgets(libraryId, payload);
            migrateCategoryBudgets(libraryId, payload);
            migrateDeletedItems(libraryId, payload);
            migrateRecurringRules(libraryId, payload);
            migrateRecurringOccurrences(libraryId, payload);

            migratedCount++;
            log.info("Migrated snapshot {} for library {}.", snapshot.getId(), libraryId);
        }

        log.info("Migration complete. Migrated {} snapshot(s).", migratedCount);
    }

    // -----------------------------------------------------------------------
    // Per-table migration
    // -----------------------------------------------------------------------

    private void migrateBooks(String libraryId, Map<String, Object> payload) {
        List<Map<String, Object>> rows = getRows(payload, "books");
        for (Map<String, Object> row : rows) {
            LedgerBookEntity entity = new LedgerBookEntity();
            entity.setId(getString(row, "id", IdGenerator.newId("lbook")));
            entity.setLibraryId(libraryId);
            entity.setName(getString(row, "name", "Default"));
            entity.setCreatorUserId(getStringOrNull(row, "creatorUserId"));
            entity.setTemplate(getString(row, "template", "default"));
            entity.setCurrencyCode(getString(row, "currencyCode", "CNY"));
            entity.setCurrencySymbol(getString(row, "currencySymbol", "\u00a5"));
            entity.setCoverColor(getLong(row, "coverColor", 0L));
            entity.setSortOrder(getInt(row, "sortOrder", 0));
            entity.setIsDeleted(getBoolean(row, "isDeleted", false));
            applyTimestamps(entity, row);
            bookRepository.save(entity);
        }
    }

    private void migrateCategories(String libraryId, Map<String, Object> payload) {
        List<Map<String, Object>> rows = getRows(payload, "categories");
        for (Map<String, Object> row : rows) {
            LedgerCategoryEntity entity = new LedgerCategoryEntity();
            entity.setId(getString(row, "id", IdGenerator.newId("lcat")));
            entity.setLibraryId(libraryId);
            entity.setBookId(getString(row, "bookId", ""));
            entity.setName(getString(row, "name", ""));
            entity.setIconKey(getString(row, "iconKey", ""));
            entity.setColor(getLong(row, "color", 0L));
            entity.setType(getEnum(row, "type", LedgerCategoryType.class, LedgerCategoryType.EXPENSE));
            entity.setSortOrder(getInt(row, "sortOrder", 0));
            entity.setHidden(getBoolean(row, "hidden", false));
            applyTimestamps(entity, row);
            categoryRepository.save(entity);
        }
    }

    private void migrateAccounts(String libraryId, Map<String, Object> payload) {
        List<Map<String, Object>> rows = getRows(payload, "accounts");
        for (Map<String, Object> row : rows) {
            LedgerAccountEntity entity = new LedgerAccountEntity();
            entity.setId(getString(row, "id", IdGenerator.newId("lacc")));
            entity.setLibraryId(libraryId);
            entity.setBookId(getString(row, "bookId", ""));
            entity.setName(getString(row, "name", ""));
            entity.setType(getEnum(row, "type", LedgerAccountType.class, LedgerAccountType.OTHER));
            entity.setIconKey(getString(row, "iconKey", ""));
            entity.setColor(getLong(row, "color", 0L));
            entity.setInitialBalanceCents(getLong(row, "initialBalanceCents", 0L));
            entity.setBalanceCents(getLong(row, "balanceCents", 0L));
            entity.setCreditLimitCents(getLongOrNull(row, "creditLimitCents"));
            entity.setIncludeInTotal(getBoolean(row, "includeInTotal", true));
            entity.setHidden(getBoolean(row, "hidden", false));
            entity.setNote(getStringOrNull(row, "note"));
            entity.setSortOrder(getInt(row, "sortOrder", 0));
            applyTimestamps(entity, row);
            accountRepository.save(entity);
        }
    }

    private void migrateTransactions(String libraryId, Map<String, Object> payload) {
        List<Map<String, Object>> rows = getRows(payload, "transactions");
        for (Map<String, Object> row : rows) {
            LedgerTransactionEntity entity = new LedgerTransactionEntity();
            entity.setId(getString(row, "id", IdGenerator.newId("ltx")));
            entity.setLibraryId(libraryId);
            entity.setBookId(getString(row, "bookId", ""));
            entity.setCategoryId(getStringOrNull(row, "categoryId"));
            entity.setAccountId(getString(row, "accountId", ""));
            entity.setToAccountId(getStringOrNull(row, "toAccountId"));
            entity.setAmountCents(getLong(row, "amountCents", 0L));
            entity.setType(getEnum(row, "type", LedgerTransactionType.class, LedgerTransactionType.EXPENSE));
            entity.setOccurredAtMillis(getLong(row, "occurredAtMillis", System.currentTimeMillis()));
            entity.setRemark(getStringOrNull(row, "remark"));
            entity.setMethod(getStringOrNull(row, "method"));
            entity.setDeletedAtMillis(getLongOrNull(row, "deletedAtMillis"));
            applyTimestamps(entity, row);
            transactionRepository.save(entity);
        }
    }

    private void migrateBudgets(String libraryId, Map<String, Object> payload) {
        List<Map<String, Object>> rows = getRows(payload, "budgets");
        for (Map<String, Object> row : rows) {
            LedgerBudgetEntity entity = new LedgerBudgetEntity();
            entity.setId(getString(row, "id", IdGenerator.newId("lbgt")));
            entity.setLibraryId(libraryId);
            entity.setBookId(getString(row, "bookId", ""));
            entity.setPeriod(getEnum(row, "period", LedgerBudgetPeriod.class, LedgerBudgetPeriod.MONTH));
            entity.setStartMillis(getLong(row, "startMillis", 0L));
            entity.setEndMillis(getLong(row, "endMillis", 0L));
            entity.setTotalAmountCents(getLong(row, "totalAmountCents", 0L));
            applyTimestamps(entity, row);
            budgetRepository.save(entity);
        }
    }

    private void migrateCategoryBudgets(String libraryId, Map<String, Object> payload) {
        List<Map<String, Object>> rows = getRows(payload, "categoryBudgets");
        for (Map<String, Object> row : rows) {
            LedgerCategoryBudgetEntity entity = new LedgerCategoryBudgetEntity();
            entity.setId(getString(row, "id", IdGenerator.newId("lcbgt")));
            entity.setLibraryId(libraryId);
            entity.setBudgetId(getString(row, "budgetId", ""));
            entity.setCategoryId(getString(row, "categoryId", ""));
            entity.setAmountCents(getLong(row, "amountCents", 0L));
            applyTimestamps(entity, row);
            categoryBudgetRepository.save(entity);
        }
    }

    private void migrateDeletedItems(String libraryId, Map<String, Object> payload) {
        List<Map<String, Object>> rows = getRows(payload, "deletedItems");
        for (Map<String, Object> row : rows) {
            LedgerDeletedItemEntity entity = new LedgerDeletedItemEntity();
            entity.setId(getString(row, "id", IdGenerator.newId("ldel")));
            entity.setLibraryId(libraryId);
            entity.setBookId(getString(row, "bookId", ""));
            entity.setItemId(getString(row, "itemId", ""));
            entity.setType(getEnum(row, "type", LedgerDeletedItemType.class, LedgerDeletedItemType.TRANSACTION));
            entity.setTitle(getString(row, "title", ""));
            entity.setAmountCents(getLong(row, "amountCents", 0L));
            entity.setDeletedAtMillis(getLong(row, "deletedAtMillis", System.currentTimeMillis()));
            entity.setExpiresAtMillis(getLong(row, "expiresAtMillis", System.currentTimeMillis()));
            applyTimestamps(entity, row);
            deletedItemRepository.save(entity);
        }
    }

    private void migrateRecurringRules(String libraryId, Map<String, Object> payload) {
        List<Map<String, Object>> rows = getRows(payload, "recurringRules");
        for (Map<String, Object> row : rows) {
            LedgerRecurringRuleEntity entity = new LedgerRecurringRuleEntity();
            entity.setId(getString(row, "id", IdGenerator.newId("lrr")));
            entity.setLibraryId(libraryId);
            entity.setBookId(getString(row, "bookId", ""));
            entity.setType(getEnum(row, "type", LedgerTransactionType.class, LedgerTransactionType.EXPENSE));
            entity.setCategoryId(getStringOrNull(row, "categoryId"));
            entity.setAccountId(getString(row, "accountId", ""));
            entity.setToAccountId(getStringOrNull(row, "toAccountId"));
            entity.setAmountCents(getLong(row, "amountCents", 0L));
            entity.setRemark(getStringOrNull(row, "remark"));
            entity.setFrequency(getEnum(row, "frequency", LedgerRecurringFrequency.class, LedgerRecurringFrequency.MONTHLY));
            entity.setStartAtMillis(getLong(row, "startAtMillis", System.currentTimeMillis()));
            entity.setEndAtMillis(getLongOrNull(row, "endAtMillis"));
            entity.setNextOccurrenceAtMillis(getLong(row, "nextOccurrenceAtMillis", System.currentTimeMillis()));
            entity.setEnabled(getBoolean(row, "enabled", true));
            applyTimestamps(entity, row);
            recurringRuleRepository.save(entity);
        }
    }

    private void migrateRecurringOccurrences(String libraryId, Map<String, Object> payload) {
        List<Map<String, Object>> rows = getRows(payload, "recurringOccurrences");
        for (Map<String, Object> row : rows) {
            LedgerRecurringOccurrenceEntity entity = new LedgerRecurringOccurrenceEntity();
            entity.setId(getString(row, "id", IdGenerator.newId("lro")));
            entity.setLibraryId(libraryId);
            entity.setRuleId(getString(row, "ruleId", ""));
            entity.setTransactionId(getString(row, "transactionId", ""));
            entity.setOccurrenceAtMillis(getLong(row, "occurrenceAtMillis", System.currentTimeMillis()));
            applyTimestamps(entity, row);
            recurringOccurrenceRepository.save(entity);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getRows(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to parse ledger snapshot payload JSON.", e);
            return null;
        }
    }

    private void applyTimestamps(com.yingshi.server.domain.LibraryScopedEntity entity, Map<String, Object> row) {
        Long createdAtMillis = getLongOrNull(row, "createdAtMillis");
        if (createdAtMillis != null) {
            entity.setCreatedAt(Instant.ofEpochMilli(createdAtMillis));
        }
        Long updatedAtMillis = getLongOrNull(row, "updatedAtMillis");
        if (updatedAtMillis != null) {
            entity.setUpdatedAt(Instant.ofEpochMilli(updatedAtMillis));
        }
    }

    private String getString(Map<String, Object> row, String key, String defaultValue) {
        Object value = row.get(key);
        if (value == null) return defaultValue;
        String str = value.toString();
        return str.isEmpty() ? defaultValue : str;
    }

    private String getStringOrNull(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value.toString() : null;
    }

    private long getLong(Map<String, Object> row, String key, long defaultValue) {
        Object value = row.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Long getLongOrNull(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int getInt(Map<String, Object> row, String key, int defaultValue) {
        Object value = row.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBoolean(Map<String, Object> row, String key, boolean defaultValue) {
        Object value = row.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    private <E extends Enum<E>> E getEnum(Map<String, Object> row, String key, Class<E> enumType, E defaultValue) {
        Object value = row.get(key);
        if (value == null) return defaultValue;
        String str = value.toString();
        if (str.isEmpty()) return defaultValue;
        try {
            return Enum.valueOf(enumType, str);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown enum value '{}' for type {}. Using default.", str, enumType.getSimpleName());
            return defaultValue;
        }
    }
}
