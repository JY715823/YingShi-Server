package com.yingshi.server.service.ledger;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
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
import com.yingshi.server.dto.ledger.DeletedRowRef;
import com.yingshi.server.dto.ledger.LedgerChangesDto;
import com.yingshi.server.dto.ledger.LedgerClientChangesDto;
import com.yingshi.server.dto.ledger.LedgerSyncRequest;
import com.yingshi.server.dto.ledger.LedgerSyncResponse;
import com.yingshi.server.dto.ledger.LedgerSyncRows.AccountRow;
import com.yingshi.server.dto.ledger.LedgerSyncRows.BookRow;
import com.yingshi.server.dto.ledger.LedgerSyncRows.BudgetRow;
import com.yingshi.server.dto.ledger.LedgerSyncRows.CategoryBudgetRow;
import com.yingshi.server.dto.ledger.LedgerSyncRows.CategoryRow;
import com.yingshi.server.dto.ledger.LedgerSyncRows.DeletedItemRow;
import com.yingshi.server.dto.ledger.LedgerSyncRows.RecurringOccurrenceRow;
import com.yingshi.server.dto.ledger.LedgerSyncRows.RecurringRuleRow;
import com.yingshi.server.dto.ledger.LedgerSyncRows.TransactionRow;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LedgerSyncService {

    private static final Logger log = LoggerFactory.getLogger(LedgerSyncService.class);

    private static final String TABLE_BOOKS = "books";
    private static final String TABLE_CATEGORIES = "categories";
    private static final String TABLE_ACCOUNTS = "accounts";
    private static final String TABLE_TRANSACTIONS = "transactions";
    private static final String TABLE_BUDGETS = "budgets";
    private static final String TABLE_CATEGORY_BUDGETS = "categoryBudgets";
    private static final String TABLE_DELETED_ITEMS = "deletedItems";
    private static final String TABLE_RECURRING_RULES = "recurringRules";
    private static final String TABLE_RECURRING_OCCURRENCES = "recurringOccurrences";

    private final LedgerBookRepository bookRepository;
    private final LedgerCategoryRepository categoryRepository;
    private final LedgerAccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerBudgetRepository budgetRepository;
    private final LedgerCategoryBudgetRepository categoryBudgetRepository;
    private final LedgerDeletedItemRepository deletedItemRepository;
    private final LedgerRecurringRuleRepository recurringRuleRepository;
    private final LedgerRecurringOccurrenceRepository recurringOccurrenceRepository;

    public LedgerSyncService(
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
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.categoryBudgetRepository = categoryBudgetRepository;
        this.deletedItemRepository = deletedItemRepository;
        this.recurringRuleRepository = recurringRuleRepository;
        this.recurringOccurrenceRepository = recurringOccurrenceRepository;
    }

    @Transactional
    public LedgerSyncResponse sync(LedgerSyncRequest request, AuthenticatedUser user) {
        String libraryId = user.libraryId();
        Instant syncStart = Instant.now();

        LedgerClientChangesDto clientChanges = request.changes();
        if (clientChanges == null) {
            clientChanges = LedgerClientChangesDto.empty();
        }

        applyChanges(libraryId, clientChanges);

        if (clientChanges.deletedRowIds() != null) {
            applyDeletions(libraryId, clientChanges.deletedRowIds());
        }

        Instant since = Instant.ofEpochMilli(request.lastSyncVersionMillis());
        LedgerChangesDto serverChanges = queryChangesSince(libraryId, since);

        return new LedgerSyncResponse(syncStart.toEpochMilli(), serverChanges);
    }

    // -----------------------------------------------------------------------
    // Apply client changes (upsert typed rows)
    // -----------------------------------------------------------------------

    private void applyChanges(String libraryId, LedgerClientChangesDto changes) {
        upsertBooks(libraryId, changes.books());
        upsertCategories(libraryId, changes.categories());
        upsertAccounts(libraryId, changes.accounts());
        upsertTransactions(libraryId, changes.transactions());
        upsertBudgets(libraryId, changes.budgets());
        upsertCategoryBudgets(libraryId, changes.categoryBudgets());
        upsertDeletedItems(libraryId, changes.deletedItems());
        upsertRecurringRules(libraryId, changes.recurringRules());
        upsertRecurringOccurrences(libraryId, changes.recurringOccurrences());
    }

    private void upsertBooks(String libraryId, List<BookRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (BookRow row : rows) {
            LedgerBookEntity entity = bookRepository.findByIdAndLibraryId(row.id(), libraryId)
                    .orElseGet(() -> {
                        LedgerBookEntity e = new LedgerBookEntity();
                        e.setId(row.id() != null ? row.id() : IdGenerator.newId("lbook"));
                        e.setLibraryId(libraryId);
                        return e;
                    });
            mapToBook(row, entity);
            bookRepository.save(entity);
        }
    }

    private void upsertCategories(String libraryId, List<CategoryRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (CategoryRow row : rows) {
            LedgerCategoryEntity entity = categoryRepository.findByIdAndLibraryId(row.id(), libraryId)
                    .orElseGet(() -> {
                        LedgerCategoryEntity e = new LedgerCategoryEntity();
                        e.setId(row.id() != null ? row.id() : IdGenerator.newId("lcat"));
                        e.setLibraryId(libraryId);
                        return e;
                    });
            mapToCategory(row, entity);
            categoryRepository.save(entity);
        }
    }

    private void upsertAccounts(String libraryId, List<AccountRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (AccountRow row : rows) {
            LedgerAccountEntity entity = accountRepository.findByIdAndLibraryId(row.id(), libraryId)
                    .orElseGet(() -> {
                        LedgerAccountEntity e = new LedgerAccountEntity();
                        e.setId(row.id() != null ? row.id() : IdGenerator.newId("lacc"));
                        e.setLibraryId(libraryId);
                        return e;
                    });
            mapToAccount(row, entity);
            accountRepository.save(entity);
        }
    }

    private void upsertTransactions(String libraryId, List<TransactionRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (TransactionRow row : rows) {
            LedgerTransactionEntity entity = transactionRepository.findByIdAndLibraryId(row.id(), libraryId)
                    .orElseGet(() -> {
                        LedgerTransactionEntity e = new LedgerTransactionEntity();
                        e.setId(row.id() != null ? row.id() : IdGenerator.newId("ltx"));
                        e.setLibraryId(libraryId);
                        return e;
                    });
            mapToTransaction(row, entity);
            transactionRepository.save(entity);
        }
    }

    private void upsertBudgets(String libraryId, List<BudgetRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (BudgetRow row : rows) {
            LedgerBudgetEntity entity = budgetRepository.findByIdAndLibraryId(row.id(), libraryId)
                    .orElseGet(() -> {
                        LedgerBudgetEntity e = new LedgerBudgetEntity();
                        e.setId(row.id() != null ? row.id() : IdGenerator.newId("lbgt"));
                        e.setLibraryId(libraryId);
                        return e;
                    });
            mapToBudget(row, entity);
            budgetRepository.save(entity);
        }
    }

    private void upsertCategoryBudgets(String libraryId, List<CategoryBudgetRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (CategoryBudgetRow row : rows) {
            LedgerCategoryBudgetEntity entity = categoryBudgetRepository.findByIdAndLibraryId(row.id(), libraryId)
                    .orElseGet(() -> {
                        LedgerCategoryBudgetEntity e = new LedgerCategoryBudgetEntity();
                        e.setId(row.id() != null ? row.id() : IdGenerator.newId("lcbgt"));
                        e.setLibraryId(libraryId);
                        return e;
                    });
            mapToCategoryBudget(row, entity);
            categoryBudgetRepository.save(entity);
        }
    }

    private void upsertDeletedItems(String libraryId, List<DeletedItemRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (DeletedItemRow row : rows) {
            LedgerDeletedItemEntity entity = deletedItemRepository.findByIdAndLibraryId(row.id(), libraryId)
                    .orElseGet(() -> {
                        LedgerDeletedItemEntity e = new LedgerDeletedItemEntity();
                        e.setId(row.id() != null ? row.id() : IdGenerator.newId("ldel"));
                        e.setLibraryId(libraryId);
                        return e;
                    });
            mapToDeletedItem(row, entity);
            deletedItemRepository.save(entity);
        }
    }

    private void upsertRecurringRules(String libraryId, List<RecurringRuleRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (RecurringRuleRow row : rows) {
            LedgerRecurringRuleEntity entity = recurringRuleRepository.findByIdAndLibraryId(row.id(), libraryId)
                    .orElseGet(() -> {
                        LedgerRecurringRuleEntity e = new LedgerRecurringRuleEntity();
                        e.setId(row.id() != null ? row.id() : IdGenerator.newId("lrr"));
                        e.setLibraryId(libraryId);
                        return e;
                    });
            mapToRecurringRule(row, entity);
            recurringRuleRepository.save(entity);
        }
    }

    private void upsertRecurringOccurrences(String libraryId, List<RecurringOccurrenceRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (RecurringOccurrenceRow row : rows) {
            LedgerRecurringOccurrenceEntity entity = recurringOccurrenceRepository.findByIdAndLibraryId(row.id(), libraryId)
                    .orElseGet(() -> {
                        LedgerRecurringOccurrenceEntity e = new LedgerRecurringOccurrenceEntity();
                        e.setId(row.id() != null ? row.id() : IdGenerator.newId("lro"));
                        e.setLibraryId(libraryId);
                        return e;
                    });
            mapToRecurringOccurrence(row, entity);
            recurringOccurrenceRepository.save(entity);
        }
    }

    // -----------------------------------------------------------------------
    // Apply deletions
    // -----------------------------------------------------------------------

    private void applyDeletions(String libraryId, List<DeletedRowRef> deletedRowIds) {
        if (deletedRowIds == null || deletedRowIds.isEmpty()) return;

        Map<String, List<String>> idsByTable = new HashMap<>();
        for (DeletedRowRef ref : deletedRowIds) {
            idsByTable.computeIfAbsent(ref.table(), k -> new ArrayList<>()).add(ref.id());
        }

        deleteIfPresent(libraryId, idsByTable.get(TABLE_BOOKS), bookRepository::deleteByLibraryIdAndIdIn);
        deleteIfPresent(libraryId, idsByTable.get(TABLE_CATEGORIES), categoryRepository::deleteByLibraryIdAndIdIn);
        deleteIfPresent(libraryId, idsByTable.get(TABLE_ACCOUNTS), accountRepository::deleteByLibraryIdAndIdIn);
        deleteIfPresent(libraryId, idsByTable.get(TABLE_TRANSACTIONS), transactionRepository::deleteByLibraryIdAndIdIn);
        deleteIfPresent(libraryId, idsByTable.get(TABLE_BUDGETS), budgetRepository::deleteByLibraryIdAndIdIn);
        deleteIfPresent(libraryId, idsByTable.get(TABLE_CATEGORY_BUDGETS), categoryBudgetRepository::deleteByLibraryIdAndIdIn);
        deleteIfPresent(libraryId, idsByTable.get(TABLE_DELETED_ITEMS), deletedItemRepository::deleteByLibraryIdAndIdIn);
        deleteIfPresent(libraryId, idsByTable.get(TABLE_RECURRING_RULES), recurringRuleRepository::deleteByLibraryIdAndIdIn);
        deleteIfPresent(libraryId, idsByTable.get(TABLE_RECURRING_OCCURRENCES), recurringOccurrenceRepository::deleteByLibraryIdAndIdIn);
    }

    private interface BulkDeleter {
        void delete(String libraryId, List<String> ids);
    }

    private void deleteIfPresent(String libraryId, List<String> ids, BulkDeleter deleter) {
        if (ids != null && !ids.isEmpty()) {
            deleter.delete(libraryId, ids);
        }
    }

    // -----------------------------------------------------------------------
    // Query server changes since a given instant (output as Map for flexibility)
    // -----------------------------------------------------------------------

    private LedgerChangesDto queryChangesSince(String libraryId, Instant since) {
        List<LedgerBookEntity> books = bookRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        List<LedgerCategoryEntity> categories = categoryRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        List<LedgerAccountEntity> accounts = accountRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        List<LedgerTransactionEntity> transactions = transactionRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        List<LedgerBudgetEntity> budgets = budgetRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        List<LedgerCategoryBudgetEntity> categoryBudgets = categoryBudgetRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        List<LedgerDeletedItemEntity> deletedItems = deletedItemRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        List<LedgerRecurringRuleEntity> recurringRules = recurringRuleRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        List<LedgerRecurringOccurrenceEntity> recurringOccurrences = recurringOccurrenceRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);

        return new LedgerChangesDto(
                books.stream().map(this::bookToMap).toList(),
                categories.stream().map(this::categoryToMap).toList(),
                accounts.stream().map(this::accountToMap).toList(),
                transactions.stream().map(this::transactionToMap).toList(),
                budgets.stream().map(this::budgetToMap).toList(),
                categoryBudgets.stream().map(this::categoryBudgetToMap).toList(),
                deletedItems.stream().map(this::deletedItemToMap).toList(),
                recurringRules.stream().map(this::recurringRuleToMap).toList(),
                recurringOccurrences.stream().map(this::recurringOccurrenceToMap).toList(),
                new ArrayList<>()
        );
    }

    // -----------------------------------------------------------------------
    // Typed Row → Entity mappers (input)
    // -----------------------------------------------------------------------

    private void mapToBook(BookRow row, LedgerBookEntity entity) {
        entity.setName(row.name());
        entity.setCreatorUserId(row.creatorUserId());
        entity.setTemplate(row.template());
        entity.setCurrencyCode(row.currencyCode());
        entity.setCurrencySymbol(row.currencySymbol());
        entity.setCoverColor(row.coverColor());
        entity.setSortOrder(row.sortOrder());
        entity.setIsDeleted(row.isDeleted() != null ? row.isDeleted() : false);
    }

    private void mapToCategory(CategoryRow row, LedgerCategoryEntity entity) {
        entity.setBookId(row.bookId());
        entity.setName(row.name());
        entity.setIconKey(row.iconKey());
        entity.setColor(row.color());
        entity.setType(parseEnum(row.type(), LedgerCategoryType.class));
        entity.setSortOrder(row.sortOrder());
        entity.setHidden(row.hidden() != null ? row.hidden() : false);
    }

    private void mapToAccount(AccountRow row, LedgerAccountEntity entity) {
        entity.setBookId(row.bookId());
        entity.setName(row.name());
        entity.setType(parseEnum(row.type(), LedgerAccountType.class));
        entity.setIconKey(row.iconKey());
        entity.setColor(row.color());
        entity.setInitialBalanceCents(row.initialBalanceCents());
        entity.setBalanceCents(row.balanceCents());
        entity.setCreditLimitCents(row.creditLimitCents());
        entity.setIncludeInTotal(row.includeInTotal() != null ? row.includeInTotal() : true);
        entity.setHidden(row.hidden() != null ? row.hidden() : false);
        entity.setNote(row.note());
        entity.setSortOrder(row.sortOrder());
    }

    private void mapToTransaction(TransactionRow row, LedgerTransactionEntity entity) {
        entity.setBookId(row.bookId());
        entity.setCategoryId(row.categoryId());
        entity.setAccountId(row.accountId());
        entity.setToAccountId(row.toAccountId());
        entity.setAmountCents(row.amountCents());
        entity.setType(parseEnum(row.type(), LedgerTransactionType.class));
        entity.setOccurredAtMillis(row.occurredAtMillis());
        entity.setRemark(row.remark());
        entity.setMethod(row.method());
        entity.setDeletedAtMillis(row.deletedAtMillis());
    }

    private void mapToBudget(BudgetRow row, LedgerBudgetEntity entity) {
        entity.setBookId(row.bookId());
        entity.setPeriod(parseEnum(row.period(), LedgerBudgetPeriod.class));
        entity.setStartMillis(row.startMillis());
        entity.setEndMillis(row.endMillis());
        entity.setTotalAmountCents(row.totalAmountCents());
    }

    private void mapToCategoryBudget(CategoryBudgetRow row, LedgerCategoryBudgetEntity entity) {
        entity.setBudgetId(row.budgetId());
        entity.setCategoryId(row.categoryId());
        entity.setAmountCents(row.amountCents());
    }

    private void mapToDeletedItem(DeletedItemRow row, LedgerDeletedItemEntity entity) {
        entity.setBookId(row.bookId());
        entity.setItemId(row.itemId());
        entity.setType(parseEnum(row.type(), LedgerDeletedItemType.class));
        entity.setTitle(row.title());
        entity.setAmountCents(row.amountCents());
        entity.setDeletedAtMillis(row.deletedAtMillis());
        entity.setExpiresAtMillis(row.expiresAtMillis());
    }

    private void mapToRecurringRule(RecurringRuleRow row, LedgerRecurringRuleEntity entity) {
        entity.setBookId(row.bookId());
        entity.setType(parseEnum(row.type(), LedgerTransactionType.class));
        entity.setCategoryId(row.categoryId());
        entity.setAccountId(row.accountId());
        entity.setToAccountId(row.toAccountId());
        entity.setAmountCents(row.amountCents());
        entity.setRemark(row.remark());
        entity.setFrequency(parseEnum(row.frequency(), LedgerRecurringFrequency.class));
        entity.setStartAtMillis(row.startAtMillis());
        entity.setEndAtMillis(row.endAtMillis());
        entity.setNextOccurrenceAtMillis(row.nextOccurrenceAtMillis());
        entity.setEnabled(row.enabled() != null ? row.enabled() : true);
    }

    private void mapToRecurringOccurrence(RecurringOccurrenceRow row, LedgerRecurringOccurrenceEntity entity) {
        entity.setRuleId(row.ruleId());
        entity.setTransactionId(row.transactionId());
        entity.setOccurrenceAtMillis(row.occurrenceAtMillis());
    }

    // -----------------------------------------------------------------------
    // Entity → Map mappers (output, preserves timestamps & metadata)
    // -----------------------------------------------------------------------

    private Map<String, Object> bookToMap(LedgerBookEntity e) {
        Map<String, Object> map = new HashMap<>();
        putIfNotNull(map, "id", e.getId());
        putIfNotNull(map, "libraryId", e.getLibraryId());
        putIfNotNull(map, "name", e.getName());
        putIfNotNull(map, "creatorUserId", e.getCreatorUserId());
        putIfNotNull(map, "template", e.getTemplate());
        putIfNotNull(map, "currencyCode", e.getCurrencyCode());
        putIfNotNull(map, "currencySymbol", e.getCurrencySymbol());
        putIfNotNull(map, "coverColor", e.getCoverColor());
        putIfNotNull(map, "sortOrder", e.getSortOrder());
        putIfNotNull(map, "isDeleted", e.getIsDeleted());
        putIfNotNull(map, "createdAtMillis", instantToMillis(e.getCreatedAt()));
        putIfNotNull(map, "updatedAtMillis", instantToMillis(e.getUpdatedAt()));
        return map;
    }

    private Map<String, Object> categoryToMap(LedgerCategoryEntity e) {
        Map<String, Object> map = new HashMap<>();
        putIfNotNull(map, "id", e.getId());
        putIfNotNull(map, "libraryId", e.getLibraryId());
        putIfNotNull(map, "bookId", e.getBookId());
        putIfNotNull(map, "name", e.getName());
        putIfNotNull(map, "iconKey", e.getIconKey());
        putIfNotNull(map, "color", e.getColor());
        putIfNotNull(map, "type", enumName(e.getType()));
        putIfNotNull(map, "sortOrder", e.getSortOrder());
        putIfNotNull(map, "hidden", e.getHidden());
        putIfNotNull(map, "createdAtMillis", instantToMillis(e.getCreatedAt()));
        putIfNotNull(map, "updatedAtMillis", instantToMillis(e.getUpdatedAt()));
        return map;
    }

    private Map<String, Object> accountToMap(LedgerAccountEntity e) {
        Map<String, Object> map = new HashMap<>();
        putIfNotNull(map, "id", e.getId());
        putIfNotNull(map, "libraryId", e.getLibraryId());
        putIfNotNull(map, "bookId", e.getBookId());
        putIfNotNull(map, "name", e.getName());
        putIfNotNull(map, "type", enumName(e.getType()));
        putIfNotNull(map, "iconKey", e.getIconKey());
        putIfNotNull(map, "color", e.getColor());
        putIfNotNull(map, "initialBalanceCents", e.getInitialBalanceCents());
        putIfNotNull(map, "balanceCents", e.getBalanceCents());
        putIfNotNull(map, "creditLimitCents", e.getCreditLimitCents());
        putIfNotNull(map, "includeInTotal", e.getIncludeInTotal());
        putIfNotNull(map, "hidden", e.getHidden());
        putIfNotNull(map, "note", e.getNote());
        putIfNotNull(map, "sortOrder", e.getSortOrder());
        putIfNotNull(map, "createdAtMillis", instantToMillis(e.getCreatedAt()));
        putIfNotNull(map, "updatedAtMillis", instantToMillis(e.getUpdatedAt()));
        return map;
    }

    private Map<String, Object> transactionToMap(LedgerTransactionEntity e) {
        Map<String, Object> map = new HashMap<>();
        putIfNotNull(map, "id", e.getId());
        putIfNotNull(map, "libraryId", e.getLibraryId());
        putIfNotNull(map, "bookId", e.getBookId());
        putIfNotNull(map, "categoryId", e.getCategoryId());
        putIfNotNull(map, "accountId", e.getAccountId());
        putIfNotNull(map, "toAccountId", e.getToAccountId());
        putIfNotNull(map, "amountCents", e.getAmountCents());
        putIfNotNull(map, "type", enumName(e.getType()));
        putIfNotNull(map, "occurredAtMillis", e.getOccurredAtMillis());
        putIfNotNull(map, "remark", e.getRemark());
        putIfNotNull(map, "method", e.getMethod());
        putIfNotNull(map, "deletedAtMillis", e.getDeletedAtMillis());
        putIfNotNull(map, "createdAtMillis", instantToMillis(e.getCreatedAt()));
        putIfNotNull(map, "updatedAtMillis", instantToMillis(e.getUpdatedAt()));
        return map;
    }

    private Map<String, Object> budgetToMap(LedgerBudgetEntity e) {
        Map<String, Object> map = new HashMap<>();
        putIfNotNull(map, "id", e.getId());
        putIfNotNull(map, "libraryId", e.getLibraryId());
        putIfNotNull(map, "bookId", e.getBookId());
        putIfNotNull(map, "period", enumName(e.getPeriod()));
        putIfNotNull(map, "startMillis", e.getStartMillis());
        putIfNotNull(map, "endMillis", e.getEndMillis());
        putIfNotNull(map, "totalAmountCents", e.getTotalAmountCents());
        putIfNotNull(map, "createdAtMillis", instantToMillis(e.getCreatedAt()));
        putIfNotNull(map, "updatedAtMillis", instantToMillis(e.getUpdatedAt()));
        return map;
    }

    private Map<String, Object> categoryBudgetToMap(LedgerCategoryBudgetEntity e) {
        Map<String, Object> map = new HashMap<>();
        putIfNotNull(map, "id", e.getId());
        putIfNotNull(map, "libraryId", e.getLibraryId());
        putIfNotNull(map, "budgetId", e.getBudgetId());
        putIfNotNull(map, "categoryId", e.getCategoryId());
        putIfNotNull(map, "amountCents", e.getAmountCents());
        putIfNotNull(map, "createdAtMillis", instantToMillis(e.getCreatedAt()));
        putIfNotNull(map, "updatedAtMillis", instantToMillis(e.getUpdatedAt()));
        return map;
    }

    private Map<String, Object> deletedItemToMap(LedgerDeletedItemEntity e) {
        Map<String, Object> map = new HashMap<>();
        putIfNotNull(map, "id", e.getId());
        putIfNotNull(map, "libraryId", e.getLibraryId());
        putIfNotNull(map, "bookId", e.getBookId());
        putIfNotNull(map, "itemId", e.getItemId());
        putIfNotNull(map, "type", enumName(e.getType()));
        putIfNotNull(map, "title", e.getTitle());
        putIfNotNull(map, "amountCents", e.getAmountCents());
        putIfNotNull(map, "deletedAtMillis", e.getDeletedAtMillis());
        putIfNotNull(map, "expiresAtMillis", e.getExpiresAtMillis());
        putIfNotNull(map, "createdAtMillis", instantToMillis(e.getCreatedAt()));
        putIfNotNull(map, "updatedAtMillis", instantToMillis(e.getUpdatedAt()));
        return map;
    }

    private Map<String, Object> recurringRuleToMap(LedgerRecurringRuleEntity e) {
        Map<String, Object> map = new HashMap<>();
        putIfNotNull(map, "id", e.getId());
        putIfNotNull(map, "libraryId", e.getLibraryId());
        putIfNotNull(map, "bookId", e.getBookId());
        putIfNotNull(map, "type", enumName(e.getType()));
        putIfNotNull(map, "categoryId", e.getCategoryId());
        putIfNotNull(map, "accountId", e.getAccountId());
        putIfNotNull(map, "toAccountId", e.getToAccountId());
        putIfNotNull(map, "amountCents", e.getAmountCents());
        putIfNotNull(map, "remark", e.getRemark());
        putIfNotNull(map, "frequency", enumName(e.getFrequency()));
        putIfNotNull(map, "startAtMillis", e.getStartAtMillis());
        putIfNotNull(map, "endAtMillis", e.getEndAtMillis());
        putIfNotNull(map, "nextOccurrenceAtMillis", e.getNextOccurrenceAtMillis());
        putIfNotNull(map, "enabled", e.getEnabled());
        putIfNotNull(map, "createdAtMillis", instantToMillis(e.getCreatedAt()));
        putIfNotNull(map, "updatedAtMillis", instantToMillis(e.getUpdatedAt()));
        return map;
    }

    private Map<String, Object> recurringOccurrenceToMap(LedgerRecurringOccurrenceEntity e) {
        Map<String, Object> map = new HashMap<>();
        putIfNotNull(map, "id", e.getId());
        putIfNotNull(map, "libraryId", e.getLibraryId());
        putIfNotNull(map, "ruleId", e.getRuleId());
        putIfNotNull(map, "transactionId", e.getTransactionId());
        putIfNotNull(map, "occurrenceAtMillis", e.getOccurrenceAtMillis());
        putIfNotNull(map, "createdAtMillis", instantToMillis(e.getCreatedAt()));
        putIfNotNull(map, "updatedAtMillis", instantToMillis(e.getUpdatedAt()));
        return map;
    }

    // -----------------------------------------------------------------------
    // Generic helpers
    // -----------------------------------------------------------------------

    private <E extends Enum<E>> E parseEnum(String value, Class<E> enumType) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to parse enum {} for value '{}'", enumType.getSimpleName(), value);
            return null;
        }
    }

    private String enumName(Enum<?> value) {
        return value != null ? value.name() : null;
    }

    private Long instantToMillis(Instant instant) {
        return instant != null ? instant.toEpochMilli() : null;
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
