package com.yingshi.server.service.ledger;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.domain.ledger.LedgerAccountEntity;
import com.yingshi.server.domain.ledger.LedgerAccountType;
import com.yingshi.server.domain.ledger.LedgerBookEntity;
import com.yingshi.server.domain.ledger.LedgerDeletedRowEntity;
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
import com.yingshi.server.dto.ledger.LedgerChangeRows.AccountChangeRow;
import com.yingshi.server.dto.ledger.LedgerChangeRows.BookChangeRow;
import com.yingshi.server.dto.ledger.LedgerChangeRows.BudgetChangeRow;
import com.yingshi.server.dto.ledger.LedgerChangeRows.CategoryBudgetChangeRow;
import com.yingshi.server.dto.ledger.LedgerChangeRows.CategoryChangeRow;
import com.yingshi.server.dto.ledger.LedgerChangeRows.DeletedItemChangeRow;
import com.yingshi.server.dto.ledger.LedgerChangeRows.RecurringOccurrenceChangeRow;
import com.yingshi.server.dto.ledger.LedgerChangeRows.RecurringRuleChangeRow;
import com.yingshi.server.dto.ledger.LedgerChangeRows.TransactionChangeRow;
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
import com.yingshi.server.repository.ledger.LedgerDeletedRowRepository;
import com.yingshi.server.repository.ledger.LedgerRecurringOccurrenceRepository;
import com.yingshi.server.repository.ledger.LedgerRecurringRuleRepository;
import com.yingshi.server.repository.ledger.LedgerTransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class LedgerSyncService {

    private static final Logger log = LoggerFactory.getLogger(LedgerSyncService.class);

    private static final String TABLE_BOOKS = "books";
    private static final String TABLE_CATEGORIES = "categories";
    private static final String TABLE_ACCOUNTS = "accounts";
    private static final String TABLE_TRANSACTIONS = "transactions";
    private static final String TABLE_BUDGETS = "budgets";
    private static final String TABLE_CATEGORY_BUDGETS = "category_budgets";
    private static final String TABLE_DELETED_ITEMS = "deleted_items";
    private static final String TABLE_RECURRING_RULES = "recurring_rules";
    private static final String TABLE_RECURRING_OCCURRENCES = "recurring_occurrences";

    private final LedgerBookRepository bookRepository;
    private final LedgerCategoryRepository categoryRepository;
    private final LedgerAccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerBudgetRepository budgetRepository;
    private final LedgerCategoryBudgetRepository categoryBudgetRepository;
    private final LedgerDeletedItemRepository deletedItemRepository;
    private final LedgerRecurringRuleRepository recurringRuleRepository;
    private final LedgerRecurringOccurrenceRepository recurringOccurrenceRepository;
    private final LedgerDeletedRowRepository deletedRowRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public LedgerSyncService(
            LedgerBookRepository bookRepository,
            LedgerCategoryRepository categoryRepository,
            LedgerAccountRepository accountRepository,
            LedgerTransactionRepository transactionRepository,
            LedgerBudgetRepository budgetRepository,
            LedgerCategoryBudgetRepository categoryBudgetRepository,
            LedgerDeletedItemRepository deletedItemRepository,
            LedgerRecurringRuleRepository recurringRuleRepository,
            LedgerRecurringOccurrenceRepository recurringOccurrenceRepository,
            LedgerDeletedRowRepository deletedRowRepository
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
        this.deletedRowRepository = deletedRowRepository;
    }

    @Transactional
    public LedgerSyncResponse sync(LedgerSyncRequest request, AuthenticatedUser user) {
        String libraryId = user.libraryId();

        LedgerClientChangesDto clientChanges = request.changes();
        if (clientChanges == null) {
            clientChanges = LedgerClientChangesDto.empty();
        }

        // 诊断日志：记录入参概要，便于定位 500 根因
        log.info("Ledger sync start: libraryId={}, lastSyncVersionMillis={}, "
                        + "books={}, categories={}, accounts={}, transactions={}, budgets={}, "
                        + "categoryBudgets={}, deletedItems={}, recurringRules={}, recurringOccurrences={}, deletedRowIds={}",
                libraryId, request.lastSyncVersionMillis(),
                size(clientChanges.books()), size(clientChanges.categories()),
                size(clientChanges.accounts()), size(clientChanges.transactions()),
                size(clientChanges.budgets()), size(clientChanges.categoryBudgets()),
                size(clientChanges.deletedItems()), size(clientChanges.recurringRules()),
                size(clientChanges.recurringOccurrences()),
                clientChanges.deletedRowIds() == null ? 0 : clientChanges.deletedRowIds().size());

        List<LedgerSyncResponse.RejectedRowRef> rejected;
        try {
            rejected = applyChanges(libraryId, clientChanges);
        } catch (RuntimeException e) {
            // 记录具体哪张表、哪条 row 触发了异常，便于客户端定位
            log.error("applyChanges failed for libraryId={}: {} - {}. "
                            + "Pending: books={}, categories={}, accounts={}, transactions={}, budgets={}, "
                            + "categoryBudgets={}, deletedItems={}, recurringRules={}, recurringOccurrences={}",
                    libraryId, e.getClass().getSimpleName(), e.getMessage(),
                    size(clientChanges.books()), size(clientChanges.categories()),
                    size(clientChanges.accounts()), size(clientChanges.transactions()),
                    size(clientChanges.budgets()), size(clientChanges.categoryBudgets()),
                    size(clientChanges.deletedItems()), size(clientChanges.recurringRules()),
                    size(clientChanges.recurringOccurrences()), e);
            throw e;
        }

        if (clientChanges.deletedRowIds() != null) {
            try {
                applyDeletions(libraryId, clientChanges.deletedRowIds());
            } catch (RuntimeException e) {
                log.error("applyDeletions failed for libraryId={}: {} - {}. deletedRowIds={}",
                        libraryId, e.getClass().getSimpleName(), e.getMessage(),
                        clientChanges.deletedRowIds(), e);
                throw e;
            }
        }

        Instant since = Instant.ofEpochMilli(request.lastSyncVersionMillis());
        LedgerChangesDto serverChanges;
        try {
            serverChanges = queryChangesSince(libraryId, since);
        } catch (RuntimeException e) {
            log.error("queryChangesSince failed for libraryId={}, since={}: {} - {}",
                    libraryId, since, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }

        // FR-19: take syncEnd AFTER queryChangesSince so next sync's since = syncEnd
        // won't re-hit rows written/updated in this request (their updatedAt <= syncEnd).
        // JPA @PreUpdate sets updatedAt during flush, which happens before queryChangesSince.
        Instant syncEnd = Instant.now();
        log.info("Ledger sync done: libraryId={}, syncEnd={}, serverChanges: books={}, transactions={}",
                libraryId, syncEnd,
                serverChanges.books() == null ? 0 : serverChanges.books().size(),
                serverChanges.transactions() == null ? 0 : serverChanges.transactions().size());
        log.info("Ledger sync rejected: count={}, details={}", rejected.size(), rejected.stream().limit(20).toList());
        return new LedgerSyncResponse(syncEnd.toEpochMilli(), serverChanges, rejected.isEmpty() ? null : rejected);
    }

    private int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    // -----------------------------------------------------------------------
    // Apply client changes (upsert typed rows)
    // -----------------------------------------------------------------------

    private java.util.List<LedgerSyncResponse.RejectedRowRef> applyChanges(
            String libraryId, LedgerClientChangesDto changes) {
        java.util.List<LedgerSyncResponse.RejectedRowRef> rejected = new java.util.ArrayList<>();
        // 显式 flush 父表，保证 FK 父子顺序，避免 Hibernate 批量重排序导致的偶发 FK 违反
        rejected.addAll(upsertBooks(libraryId, changes.books()));
        bookRepository.flush();
        rejected.addAll(upsertCategories(libraryId, changes.categories()));
        categoryRepository.flush();
        rejected.addAll(upsertAccounts(libraryId, changes.accounts()));
        accountRepository.flush();
        rejected.addAll(upsertTransactions(libraryId, changes.transactions()));
        transactionRepository.flush();
        rejected.addAll(upsertBudgets(libraryId, changes.budgets()));
        budgetRepository.flush();
        rejected.addAll(upsertCategoryBudgets(libraryId, changes.categoryBudgets()));
        categoryBudgetRepository.flush();
        rejected.addAll(upsertDeletedItems(libraryId, changes.deletedItems()));
        deletedItemRepository.flush();
        rejected.addAll(upsertRecurringRules(libraryId, changes.recurringRules()));
        recurringRuleRepository.flush();
        rejected.addAll(upsertRecurringOccurrences(libraryId, changes.recurringOccurrences()));
        recurringOccurrenceRepository.flush();
        return rejected;
    }

    private java.util.List<LedgerSyncResponse.RejectedRowRef> upsertBooks(String libraryId, List<BookRow> rows) {
        java.util.List<LedgerSyncResponse.RejectedRowRef> rejected = new java.util.ArrayList<>();
        if (rows == null || rows.isEmpty()) return rejected;
        List<String> ids = rows.stream().map(BookRow::id).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return rejected;
        Map<String, LedgerBookEntity> existing = bookRepository
                .findByLibraryIdAndIdIn(libraryId, ids)
                .stream().collect(Collectors.toMap(LedgerBookEntity::getId, e -> e));
        List<LedgerBookEntity> toSave = new ArrayList<>(rows.size());
        for (BookRow row : rows) {
            LedgerBookEntity entity = existing.get(row.id());
            if (entity == null) {
                entity = new LedgerBookEntity();
                entity.setId(row.id() != null ? row.id() : IdGenerator.newId("lbook"));
                entity.setLibraryId(libraryId);
            }
            mapToBook(row, entity);
            toSave.add(entity);
        }
        bookRepository.saveAll(toSave);
        return rejected;
    }

    private java.util.List<LedgerSyncResponse.RejectedRowRef> upsertCategories(String libraryId, List<CategoryRow> rows) {
        java.util.List<LedgerSyncResponse.RejectedRowRef> rejected = new java.util.ArrayList<>();
        if (rows == null || rows.isEmpty()) return rejected;
        List<String> ids = rows.stream().map(CategoryRow::id).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return rejected;

        // FK 预检：ledger_categories.book_id NOT NULL + FK ON DELETE CASCADE
        // 防御客户端发送未同步 bookId 或空 bookId 导致的 500 死循环
        java.util.Set<String> bookIdsToCheck = rows.stream()
                .map(CategoryRow::bookId)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        java.util.Set<String> existingBookIds = bookIdsToCheck.isEmpty()
                ? java.util.Collections.emptySet()
                : bookRepository.findByLibraryIdAndIdIn(libraryId, bookIdsToCheck)
                        .stream().map(LedgerBookEntity::getId).collect(Collectors.toSet());

        Map<String, LedgerCategoryEntity> existing = categoryRepository
                .findByLibraryIdAndIdIn(libraryId, ids)
                .stream().collect(Collectors.toMap(LedgerCategoryEntity::getId, e -> e));
        List<LedgerCategoryEntity> toSave = new ArrayList<>(rows.size());
        for (CategoryRow row : rows) {
            if (row.id() == null) {
                log.warn("upsertCategory skipped: row.id() is null");
                rejected.add(new LedgerSyncResponse.RejectedRowRef("categories", null, "null_id"));
                continue;
            }
            if (row.bookId() == null || row.bookId().isBlank()) {
                log.warn("upsertCategory skipped: id={} has blank bookId", row.id());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("categories", row.id(), "blank_book_id"));
                continue;
            }
            if (!existingBookIds.contains(row.bookId())) {
                log.warn("upsertCategory skipped: id={} references non-existent bookId={}", row.id(), row.bookId());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("categories", row.id(), "book_not_found:" + row.bookId()));
                continue;
            }
            LedgerCategoryEntity entity = existing.get(row.id());
            if (entity == null) {
                entity = new LedgerCategoryEntity();
                entity.setId(row.id() != null ? row.id() : IdGenerator.newId("lcat"));
                entity.setLibraryId(libraryId);
            }
            mapToCategory(row, entity);
            toSave.add(entity);
        }
        if (!toSave.isEmpty()) {
            categoryRepository.saveAll(toSave);
        }
        return rejected;
    }

    private java.util.List<LedgerSyncResponse.RejectedRowRef> upsertAccounts(String libraryId, List<AccountRow> rows) {
        java.util.List<LedgerSyncResponse.RejectedRowRef> rejected = new java.util.ArrayList<>();
        if (rows == null || rows.isEmpty()) return rejected;
        List<String> ids = rows.stream().map(AccountRow::id).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return rejected;

        // FK 预检：聚合所有 account 引用的 bookId，查询其在当前 library 下是否真实存在。
        // 防御旧版客户端 saveAccount 写入 bookId="" 导致的 FK 违反 500 死循环。
        java.util.Set<String> bookIdsToCheck = rows.stream()
                .map(AccountRow::bookId)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        java.util.Set<String> existingBookIds = bookIdsToCheck.isEmpty()
                ? java.util.Collections.emptySet()
                : bookRepository.findByLibraryIdAndIdIn(libraryId, bookIdsToCheck)
                        .stream().map(LedgerBookEntity::getId).collect(Collectors.toSet());

        Map<String, LedgerAccountEntity> existing = accountRepository
                .findByLibraryIdAndIdIn(libraryId, ids)
                .stream().collect(Collectors.toMap(LedgerAccountEntity::getId, e -> e));
        List<LedgerAccountEntity> toSave = new ArrayList<>(rows.size());
        for (AccountRow row : rows) {
            if (row.id() == null) {
                log.warn("upsertAccount skipped: row.id() is null");
                rejected.add(new LedgerSyncResponse.RejectedRowRef("accounts", null, "null_id"));
                continue;
            }
            // FK 预检：跳过 bookId 为空或引用了不存在 book 的 account
            if (row.bookId() == null || row.bookId().isBlank()) {
                log.warn("upsertAccount skipped: id={} has blank bookId", row.id());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("accounts", row.id(), "blank_book_id"));
                continue;
            }
            if (!existingBookIds.contains(row.bookId())) {
                log.warn("upsertAccount skipped: id={} references non-existent bookId={}", row.id(), row.bookId());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("accounts", row.id(), "book_not_found:" + row.bookId()));
                continue;
            }
            LedgerAccountEntity entity = existing.get(row.id());
            if (entity == null) {
                entity = new LedgerAccountEntity();
                entity.setId(row.id());
                entity.setLibraryId(libraryId);
            }
            mapToAccount(row, entity);
            toSave.add(entity);
        }
        if (!toSave.isEmpty()) {
            accountRepository.saveAll(toSave);
        }
        return rejected;
    }

    private java.util.List<LedgerSyncResponse.RejectedRowRef> upsertTransactions(String libraryId, List<TransactionRow> rows) {
        java.util.List<LedgerSyncResponse.RejectedRowRef> rejected = new java.util.ArrayList<>();
        if (rows == null || rows.isEmpty()) return rejected;
        List<String> ids = rows.stream().map(TransactionRow::id).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return rejected;

        // FK 预检：聚合所有 transaction 引用的 bookId / accountId / categoryId，
        // 一次性查询其在当前 library 下是否真实存在；对不存在的引用，
        // 直接跳过对应 row 而非让 flush/commit 阶段抛 ConstraintViolationException。
        // 这是 500 死循环的兜底防御：即使客户端 changelog 顺序异常或种子数据未同步，
        // 也不会因单条违规 row 让整批 sync 失败。
        java.util.Set<String> bookIdsToCheck = rows.stream()
                .map(TransactionRow::bookId)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        java.util.Set<String> accountIdsToCheck = rows.stream()
                .map(TransactionRow::accountId)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        java.util.Set<String> categoryIdsToCheck = rows.stream()
                .map(TransactionRow::categoryId)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        java.util.Set<String> existingBookIds = bookIdsToCheck.isEmpty()
                ? java.util.Collections.emptySet()
                : bookRepository.findByLibraryIdAndIdIn(libraryId, bookIdsToCheck)
                        .stream().map(LedgerBookEntity::getId).collect(Collectors.toSet());
        java.util.Set<String> existingAccountIds = accountIdsToCheck.isEmpty()
                ? java.util.Collections.emptySet()
                : accountRepository.findByLibraryIdAndIdIn(libraryId, accountIdsToCheck)
                        .stream().map(LedgerAccountEntity::getId).collect(Collectors.toSet());
        java.util.Set<String> existingCategoryIds = categoryIdsToCheck.isEmpty()
                ? java.util.Collections.emptySet()
                : categoryRepository.findByLibraryIdAndIdIn(libraryId, categoryIdsToCheck)
                        .stream().map(LedgerCategoryEntity::getId).collect(Collectors.toSet());

        Map<String, LedgerTransactionEntity> existing = transactionRepository
                .findByLibraryIdAndIdIn(libraryId, ids)
                .stream().collect(Collectors.toMap(LedgerTransactionEntity::getId, e -> e));
        List<LedgerTransactionEntity> toSave = new ArrayList<>(rows.size());
        for (TransactionRow row : rows) {
            // 诊断日志：逐条记录，便于在 500 时定位是哪条 row 出问题
            log.info("upsertTransaction: id={}, bookId={}, categoryId={}, accountId={}, toAccountId={}, "
                            + "amountCents={}, type={}, occurredAtMillis={}, method={}, deletedAtMillis={}",
                    row.id(), row.bookId(), row.categoryId(), row.accountId(), row.toAccountId(),
                    row.amountCents(), row.type(), row.occurredAtMillis(), row.method(), row.deletedAtMillis());
            if (row.id() == null) {
                log.warn("upsertTransaction skipped: row.id() is null");
                rejected.add(new LedgerSyncResponse.RejectedRowRef("transactions", null, "null_id"));
                continue;
            }
            // book_id / account_id 是 NOT NULL → 直接拒绝
            if (row.bookId() == null || row.bookId().isBlank()) {
                log.warn("upsertTransaction skipped: id={} has blank bookId", row.id());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("transactions", row.id(), "blank_book_id"));
                continue;
            }
            if (!existingBookIds.contains(row.bookId())) {
                log.warn("upsertTransaction skipped: bookId={} not found in library {}", row.bookId(), libraryId);
                rejected.add(new LedgerSyncResponse.RejectedRowRef("transactions", row.id(), "book_not_found:" + row.bookId()));
                continue;
            }
            if (row.accountId() == null || row.accountId().isBlank()) {
                log.warn("upsertTransaction skipped: id={} has blank accountId", row.id());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("transactions", row.id(), "blank_account_id"));
                continue;
            }
            if (!existingAccountIds.contains(row.accountId())) {
                log.warn("upsertTransaction skipped: accountId={} not found in library {}", row.accountId(), libraryId);
                rejected.add(new LedgerSyncResponse.RejectedRowRef("transactions", row.id(), "account_not_found:" + row.accountId()));
                continue;
            }
            // categoryId / toAccountId 是 NULLABLE + FK ON DELETE SET NULL
            // → 若引用不存在则置空，而非拒绝 row（与 DB FK ON DELETE SET NULL 语义一致）
            String resolvedCategoryId = (row.categoryId() != null && !row.categoryId().isBlank()
                    && existingCategoryIds.contains(row.categoryId()))
                    ? row.categoryId() : null;
            String resolvedToAccountId = (row.toAccountId() != null && !row.toAccountId().isBlank()
                    && existingAccountIds.contains(row.toAccountId()))
                    ? row.toAccountId() : null;
            LedgerTransactionEntity entity = existing.get(row.id());
            if (entity == null) {
                entity = new LedgerTransactionEntity();
                entity.setId(row.id());
                entity.setLibraryId(libraryId);
            }
            mapToTransaction(row, entity);
            // 用预检结果覆盖 mapToTransaction 写入的可疑 nullable FK 值，避免 commit 时 FK 违反
            entity.setCategoryId(resolvedCategoryId);
            entity.setToAccountId(resolvedToAccountId);
            toSave.add(entity);
        }
        if (!toSave.isEmpty()) {
            try {
                transactionRepository.saveAll(toSave);
                transactionRepository.flush();
            } catch (DataIntegrityViolationException ex) {
                // R1-F-4: 转账 dedupe 唯一约束等冲突，回退到逐条保存以定位冲突 row
                log.warn("upsertTransaction batch constraint violation, falling back to per-row: {}", ex.getMessage());
                entityManager.clear();
                for (LedgerTransactionEntity entity : toSave) {
                    try {
                        transactionRepository.save(entity);
                        transactionRepository.flush();
                    } catch (DataIntegrityViolationException perEx) {
                        log.warn("upsertTransaction rejected duplicate: id={}", entity.getId());
                        rejected.add(new LedgerSyncResponse.RejectedRowRef("transactions", entity.getId(), "duplicate"));
                        entityManager.clear();
                    }
                }
            }
        }
        return rejected;
    }

    private java.util.List<LedgerSyncResponse.RejectedRowRef> upsertBudgets(String libraryId, List<BudgetRow> rows) {
        java.util.List<LedgerSyncResponse.RejectedRowRef> rejected = new java.util.ArrayList<>();
        if (rows == null || rows.isEmpty()) return rejected;
        List<String> ids = rows.stream().map(BudgetRow::id).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return rejected;

        // FK 预检：ledger_budgets.book_id NOT NULL + FK ON DELETE CASCADE
        java.util.Set<String> bookIdsToCheck = rows.stream()
                .map(BudgetRow::bookId)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        java.util.Set<String> existingBookIds = bookIdsToCheck.isEmpty()
                ? java.util.Collections.emptySet()
                : bookRepository.findByLibraryIdAndIdIn(libraryId, bookIdsToCheck)
                        .stream().map(LedgerBookEntity::getId).collect(Collectors.toSet());

        Map<String, LedgerBudgetEntity> existing = budgetRepository
                .findByLibraryIdAndIdIn(libraryId, ids)
                .stream().collect(Collectors.toMap(LedgerBudgetEntity::getId, e -> e));
        List<LedgerBudgetEntity> toSave = new ArrayList<>(rows.size());
        for (BudgetRow row : rows) {
            if (row.id() == null) {
                log.warn("upsertBudget skipped: row.id() is null");
                rejected.add(new LedgerSyncResponse.RejectedRowRef("budgets", null, "null_id"));
                continue;
            }
            if (row.bookId() == null || row.bookId().isBlank()) {
                log.warn("upsertBudget skipped: id={} has blank bookId", row.id());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("budgets", row.id(), "blank_book_id"));
                continue;
            }
            if (!existingBookIds.contains(row.bookId())) {
                log.warn("upsertBudget skipped: id={} references non-existent bookId={}", row.id(), row.bookId());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("budgets", row.id(), "book_not_found:" + row.bookId()));
                continue;
            }
            LedgerBudgetEntity entity = existing.get(row.id());
            if (entity == null) {
                entity = new LedgerBudgetEntity();
                entity.setId(row.id() != null ? row.id() : IdGenerator.newId("lbgt"));
                entity.setLibraryId(libraryId);
            }
            mapToBudget(row, entity);
            toSave.add(entity);
        }
        if (!toSave.isEmpty()) {
            budgetRepository.saveAll(toSave);
        }
        return rejected;
    }

    private java.util.List<LedgerSyncResponse.RejectedRowRef> upsertCategoryBudgets(String libraryId, List<CategoryBudgetRow> rows) {
        java.util.List<LedgerSyncResponse.RejectedRowRef> rejected = new java.util.ArrayList<>();
        if (rows == null || rows.isEmpty()) return rejected;
        List<String> ids = rows.stream().map(CategoryBudgetRow::id).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return rejected;

        // FK 预检：budget_id / category_id 均 NOT NULL + FK ON DELETE CASCADE
        java.util.Set<String> budgetIdsToCheck = rows.stream()
                .map(CategoryBudgetRow::budgetId)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        java.util.Set<String> categoryIdsToCheck = rows.stream()
                .map(CategoryBudgetRow::categoryId)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        java.util.Set<String> existingBudgetIds = budgetIdsToCheck.isEmpty()
                ? java.util.Collections.emptySet()
                : budgetRepository.findByLibraryIdAndIdIn(libraryId, budgetIdsToCheck)
                        .stream().map(LedgerBudgetEntity::getId).collect(Collectors.toSet());
        java.util.Set<String> existingCategoryIds = categoryIdsToCheck.isEmpty()
                ? java.util.Collections.emptySet()
                : categoryRepository.findByLibraryIdAndIdIn(libraryId, categoryIdsToCheck)
                        .stream().map(LedgerCategoryEntity::getId).collect(Collectors.toSet());

        Map<String, LedgerCategoryBudgetEntity> existing = categoryBudgetRepository
                .findByLibraryIdAndIdIn(libraryId, ids)
                .stream().collect(Collectors.toMap(LedgerCategoryBudgetEntity::getId, e -> e));
        List<LedgerCategoryBudgetEntity> toSave = new ArrayList<>(rows.size());
        for (CategoryBudgetRow row : rows) {
            if (row.id() == null) {
                log.warn("upsertCategoryBudget skipped: row.id() is null");
                rejected.add(new LedgerSyncResponse.RejectedRowRef("category_budgets", null, "null_id"));
                continue;
            }
            if (row.budgetId() == null || row.budgetId().isBlank()) {
                log.warn("upsertCategoryBudget skipped: id={} has blank budgetId", row.id());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("category_budgets", row.id(), "blank_budget_id"));
                continue;
            }
            if (!existingBudgetIds.contains(row.budgetId())) {
                log.warn("upsertCategoryBudget skipped: id={} references non-existent budgetId={}", row.id(), row.budgetId());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("category_budgets", row.id(), "budget_not_found:" + row.budgetId()));
                continue;
            }
            if (row.categoryId() == null || row.categoryId().isBlank()) {
                log.warn("upsertCategoryBudget skipped: id={} has blank categoryId", row.id());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("category_budgets", row.id(), "blank_category_id"));
                continue;
            }
            if (!existingCategoryIds.contains(row.categoryId())) {
                log.warn("upsertCategoryBudget skipped: id={} references non-existent categoryId={}", row.id(), row.categoryId());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("category_budgets", row.id(), "category_not_found:" + row.categoryId()));
                continue;
            }
            LedgerCategoryBudgetEntity entity = existing.get(row.id());
            if (entity == null) {
                entity = new LedgerCategoryBudgetEntity();
                entity.setId(row.id() != null ? row.id() : IdGenerator.newId("lcbgt"));
                entity.setLibraryId(libraryId);
            }
            mapToCategoryBudget(row, entity);
            toSave.add(entity);
        }
        if (!toSave.isEmpty()) {
            categoryBudgetRepository.saveAll(toSave);
        }
        return rejected;
    }

    private java.util.List<LedgerSyncResponse.RejectedRowRef> upsertDeletedItems(String libraryId, List<DeletedItemRow> rows) {
        java.util.List<LedgerSyncResponse.RejectedRowRef> rejected = new java.util.ArrayList<>();
        if (rows == null || rows.isEmpty()) return rejected;
        List<String> ids = rows.stream().map(DeletedItemRow::id).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return rejected;

        // FK 预检：ledger_deleted_items.book_id NOT NULL（entity 声明） + FK ON DELETE SET NULL
        // 由于 entity 标注 nullable=false，按 NOT NULL 处理：拒绝空值/未找到的 bookId
        java.util.Set<String> bookIdsToCheck = rows.stream()
                .map(DeletedItemRow::bookId)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        java.util.Set<String> existingBookIds = bookIdsToCheck.isEmpty()
                ? java.util.Collections.emptySet()
                : bookRepository.findByLibraryIdAndIdIn(libraryId, bookIdsToCheck)
                        .stream().map(LedgerBookEntity::getId).collect(Collectors.toSet());

        Map<String, LedgerDeletedItemEntity> existing = deletedItemRepository
                .findByLibraryIdAndIdIn(libraryId, ids)
                .stream().collect(Collectors.toMap(LedgerDeletedItemEntity::getId, e -> e));
        List<LedgerDeletedItemEntity> toSave = new ArrayList<>(rows.size());
        for (DeletedItemRow row : rows) {
            if (row.id() == null) {
                log.warn("upsertDeletedItem skipped: row.id() is null");
                rejected.add(new LedgerSyncResponse.RejectedRowRef("deleted_items", null, "null_id"));
                continue;
            }
            if (row.bookId() == null || row.bookId().isBlank()) {
                log.warn("upsertDeletedItem skipped: id={} has blank bookId", row.id());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("deleted_items", row.id(), "blank_book_id"));
                continue;
            }
            if (!existingBookIds.contains(row.bookId())) {
                log.warn("upsertDeletedItem skipped: id={} references non-existent bookId={}", row.id(), row.bookId());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("deleted_items", row.id(), "book_not_found:" + row.bookId()));
                continue;
            }
            LedgerDeletedItemEntity entity = existing.get(row.id());
            if (entity == null) {
                entity = new LedgerDeletedItemEntity();
                entity.setId(row.id() != null ? row.id() : IdGenerator.newId("ldel"));
                entity.setLibraryId(libraryId);
            }
            mapToDeletedItem(row, entity);
            toSave.add(entity);
        }
        if (!toSave.isEmpty()) {
            deletedItemRepository.saveAll(toSave);
        }
        return rejected;
    }

    private java.util.List<LedgerSyncResponse.RejectedRowRef> upsertRecurringRules(String libraryId, List<RecurringRuleRow> rows) {
        java.util.List<LedgerSyncResponse.RejectedRowRef> rejected = new java.util.ArrayList<>();
        if (rows == null || rows.isEmpty()) return rejected;
        List<String> ids = rows.stream().map(RecurringRuleRow::id).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return rejected;

        // FK 预检：
        // - book_id NOT NULL + FK ON DELETE CASCADE → 拒绝
        // - account_id NOT NULL + FK ON DELETE SET NULL → 拒绝（entity 标注 nullable=false）
        // - category_id NULLABLE + FK ON DELETE SET NULL → 置空
        // - to_account_id NULLABLE + FK ON DELETE SET NULL → 置空
        java.util.Set<String> bookIdsToCheck = rows.stream()
                .map(RecurringRuleRow::bookId)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        java.util.Set<String> accountIdsToCheck = rows.stream()
                .map(RecurringRuleRow::accountId)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        java.util.Set<String> categoryIdsToCheck = rows.stream()
                .map(RecurringRuleRow::categoryId)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        java.util.Set<String> existingBookIds = bookIdsToCheck.isEmpty()
                ? java.util.Collections.emptySet()
                : bookRepository.findByLibraryIdAndIdIn(libraryId, bookIdsToCheck)
                        .stream().map(LedgerBookEntity::getId).collect(Collectors.toSet());
        java.util.Set<String> existingAccountIds = accountIdsToCheck.isEmpty()
                ? java.util.Collections.emptySet()
                : accountRepository.findByLibraryIdAndIdIn(libraryId, accountIdsToCheck)
                        .stream().map(LedgerAccountEntity::getId).collect(Collectors.toSet());
        java.util.Set<String> existingCategoryIds = categoryIdsToCheck.isEmpty()
                ? java.util.Collections.emptySet()
                : categoryRepository.findByLibraryIdAndIdIn(libraryId, categoryIdsToCheck)
                        .stream().map(LedgerCategoryEntity::getId).collect(Collectors.toSet());

        Map<String, LedgerRecurringRuleEntity> existing = recurringRuleRepository
                .findByLibraryIdAndIdIn(libraryId, ids)
                .stream().collect(Collectors.toMap(LedgerRecurringRuleEntity::getId, e -> e));
        List<LedgerRecurringRuleEntity> toSave = new ArrayList<>(rows.size());
        for (RecurringRuleRow row : rows) {
            if (row.id() == null) {
                log.warn("upsertRecurringRule skipped: row.id() is null");
                rejected.add(new LedgerSyncResponse.RejectedRowRef("recurring_rules", null, "null_id"));
                continue;
            }
            if (row.bookId() == null || row.bookId().isBlank()) {
                log.warn("upsertRecurringRule skipped: id={} has blank bookId", row.id());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("recurring_rules", row.id(), "blank_book_id"));
                continue;
            }
            if (!existingBookIds.contains(row.bookId())) {
                log.warn("upsertRecurringRule skipped: id={} references non-existent bookId={}", row.id(), row.bookId());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("recurring_rules", row.id(), "book_not_found:" + row.bookId()));
                continue;
            }
            if (row.accountId() == null || row.accountId().isBlank()) {
                log.warn("upsertRecurringRule skipped: id={} has blank accountId", row.id());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("recurring_rules", row.id(), "blank_account_id"));
                continue;
            }
            if (!existingAccountIds.contains(row.accountId())) {
                log.warn("upsertRecurringRule skipped: id={} references non-existent accountId={}", row.id(), row.accountId());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("recurring_rules", row.id(), "account_not_found:" + row.accountId()));
                continue;
            }
            // nullable FK: categoryId / toAccountId → 置空而非拒绝
            String resolvedCategoryId = (row.categoryId() != null && !row.categoryId().isBlank()
                    && existingCategoryIds.contains(row.categoryId()))
                    ? row.categoryId() : null;
            String resolvedToAccountId = (row.toAccountId() != null && !row.toAccountId().isBlank()
                    && existingAccountIds.contains(row.toAccountId()))
                    ? row.toAccountId() : null;
            LedgerRecurringRuleEntity entity = existing.get(row.id());
            if (entity == null) {
                entity = new LedgerRecurringRuleEntity();
                entity.setId(row.id() != null ? row.id() : IdGenerator.newId("lrr"));
                entity.setLibraryId(libraryId);
            }
            mapToRecurringRule(row, entity);
            entity.setCategoryId(resolvedCategoryId);
            entity.setToAccountId(resolvedToAccountId);
            toSave.add(entity);
        }
        if (!toSave.isEmpty()) {
            recurringRuleRepository.saveAll(toSave);
        }
        return rejected;
    }

    private java.util.List<LedgerSyncResponse.RejectedRowRef> upsertRecurringOccurrences(String libraryId, List<RecurringOccurrenceRow> rows) {
        java.util.List<LedgerSyncResponse.RejectedRowRef> rejected = new java.util.ArrayList<>();
        if (rows == null || rows.isEmpty()) return rejected;
        List<String> ids = rows.stream().map(RecurringOccurrenceRow::id).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return rejected;

        // FK 预检：rule_id / transaction_id 均 NOT NULL + FK ON DELETE CASCADE → 拒绝
        java.util.Set<String> ruleIdsToCheck = rows.stream()
                .map(RecurringOccurrenceRow::ruleId)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        java.util.Set<String> txIdsToCheck = rows.stream()
                .map(RecurringOccurrenceRow::transactionId)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        java.util.Set<String> existingRuleIds = ruleIdsToCheck.isEmpty()
                ? java.util.Collections.emptySet()
                : recurringRuleRepository.findByLibraryIdAndIdIn(libraryId, ruleIdsToCheck)
                        .stream().map(LedgerRecurringRuleEntity::getId).collect(Collectors.toSet());
        java.util.Set<String> existingTxIds = txIdsToCheck.isEmpty()
                ? java.util.Collections.emptySet()
                : transactionRepository.findByLibraryIdAndIdIn(libraryId, txIdsToCheck)
                        .stream().map(LedgerTransactionEntity::getId).collect(Collectors.toSet());

        Map<String, LedgerRecurringOccurrenceEntity> existing = recurringOccurrenceRepository
                .findByLibraryIdAndIdIn(libraryId, ids)
                .stream().collect(Collectors.toMap(LedgerRecurringOccurrenceEntity::getId, e -> e));
        List<LedgerRecurringOccurrenceEntity> toSave = new ArrayList<>(rows.size());
        for (RecurringOccurrenceRow row : rows) {
            if (row.id() == null) {
                log.warn("upsertRecurringOccurrence skipped: row.id() is null");
                rejected.add(new LedgerSyncResponse.RejectedRowRef("recurring_occurrences", null, "null_id"));
                continue;
            }
            if (row.ruleId() == null || row.ruleId().isBlank()) {
                log.warn("upsertRecurringOccurrence skipped: id={} has blank ruleId", row.id());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("recurring_occurrences", row.id(), "blank_rule_id"));
                continue;
            }
            if (!existingRuleIds.contains(row.ruleId())) {
                log.warn("upsertRecurringOccurrence skipped: id={} references non-existent ruleId={}", row.id(), row.ruleId());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("recurring_occurrences", row.id(), "rule_not_found:" + row.ruleId()));
                continue;
            }
            if (row.transactionId() == null || row.transactionId().isBlank()) {
                log.warn("upsertRecurringOccurrence skipped: id={} has blank transactionId", row.id());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("recurring_occurrences", row.id(), "blank_transaction_id"));
                continue;
            }
            if (!existingTxIds.contains(row.transactionId())) {
                log.warn("upsertRecurringOccurrence skipped: id={} references non-existent transactionId={}", row.id(), row.transactionId());
                rejected.add(new LedgerSyncResponse.RejectedRowRef("recurring_occurrences", row.id(), "transaction_not_found:" + row.transactionId()));
                continue;
            }
            LedgerRecurringOccurrenceEntity entity = existing.get(row.id());
            if (entity == null) {
                entity = new LedgerRecurringOccurrenceEntity();
                entity.setId(row.id() != null ? row.id() : IdGenerator.newId("lro"));
                entity.setLibraryId(libraryId);
            }
            mapToRecurringOccurrence(row, entity);
            toSave.add(entity);
        }
        if (!toSave.isEmpty()) {
            try {
                recurringOccurrenceRepository.saveAll(toSave);
                recurringOccurrenceRepository.flush();
            } catch (DataIntegrityViolationException ex) {
                // R1-F-4: recurring occurrence 唯一约束冲突，回退到逐条保存以定位冲突 row
                log.warn("upsertRecurringOccurrence batch constraint violation, falling back to per-row: {}", ex.getMessage());
                entityManager.clear();
                for (LedgerRecurringOccurrenceEntity entity : toSave) {
                    try {
                        recurringOccurrenceRepository.save(entity);
                        recurringOccurrenceRepository.flush();
                    } catch (DataIntegrityViolationException perEx) {
                        log.warn("upsertRecurringOccurrence rejected duplicate: id={}", entity.getId());
                        rejected.add(new LedgerSyncResponse.RejectedRowRef("recurring_occurrences", entity.getId(), "duplicate"));
                        entityManager.clear();
                    }
                }
            }
        }
        return rejected;
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

        Instant now = Instant.now();
        long deletedAtMillis = now.toEpochMilli();

        // V51: 为每条被删除的行记录墓碑，使共享 library 的其他客户端在后续同步时
        // 通过 queryChangesSince 收到 deletedRowIds 并清理本地副本（修复"一个账号删除、
        // 另一个账号复活"的跨账号不一致）。
        recordDeletionTombstones(libraryId, deletedRowIds, now);

        // FR-3: books keeps hard delete (uses isDeleted boolean archive, applyDeletions = permanent)
        deleteIfPresent(libraryId, idsByTable.get(TABLE_BOOKS), bookRepository::deleteByLibraryIdAndIdIn);
        // FR-3: deleted_items keeps hard delete (deletedAtMillis is a business NOT NULL field, not a soft-delete marker)
        deleteIfPresent(libraryId, idsByTable.get(TABLE_DELETED_ITEMS), deletedItemRepository::deleteByLibraryIdAndIdIn);

        // FR-3: 7 tables soft delete (set deletedAtMillis + updatedAt, propagate via updatedAt > since on next sync)
        softDeleteIfPresent(libraryId, idsByTable.get(TABLE_CATEGORIES), now, deletedAtMillis, categoryRepository::softDeleteByLibraryIdAndIdIn);
        softDeleteIfPresent(libraryId, idsByTable.get(TABLE_ACCOUNTS), now, deletedAtMillis, accountRepository::softDeleteByLibraryIdAndIdIn);
        softDeleteIfPresent(libraryId, idsByTable.get(TABLE_TRANSACTIONS), now, deletedAtMillis, transactionRepository::softDeleteByLibraryIdAndIdIn);
        softDeleteIfPresent(libraryId, idsByTable.get(TABLE_BUDGETS), now, deletedAtMillis, budgetRepository::softDeleteByLibraryIdAndIdIn);
        softDeleteIfPresent(libraryId, idsByTable.get(TABLE_CATEGORY_BUDGETS), now, deletedAtMillis, categoryBudgetRepository::softDeleteByLibraryIdAndIdIn);
        softDeleteIfPresent(libraryId, idsByTable.get(TABLE_RECURRING_RULES), now, deletedAtMillis, recurringRuleRepository::softDeleteByLibraryIdAndIdIn);
        softDeleteIfPresent(libraryId, idsByTable.get(TABLE_RECURRING_OCCURRENCES), now, deletedAtMillis, recurringOccurrenceRepository::softDeleteByLibraryIdAndIdIn);
    }

    /** 墓碑保留窗口：超过 30 天的删除记录清理掉（客户端超过 30 天未同步才会错过删除指令）。 */
    private static final long TOMBSTONE_RETENTION_DAYS = 30;

    private void recordDeletionTombstones(String libraryId, List<DeletedRowRef> deletedRowIds, Instant deletedAt) {
        List<LedgerDeletedRowEntity> tombstones = new ArrayList<>(deletedRowIds.size());
        for (DeletedRowRef ref : deletedRowIds) {
            if (ref.table() == null || ref.id() == null || ref.id().isBlank()) continue;
            LedgerDeletedRowEntity entity = new LedgerDeletedRowEntity();
            entity.setLibraryId(libraryId);
            entity.setTableName(ref.table());
            entity.setRowId(ref.id());
            entity.setDeletedAt(deletedAt);
            tombstones.add(entity);
        }
        if (!tombstones.isEmpty()) {
            deletedRowRepository.saveAll(tombstones);
        }
        // 顺手清理过期墓碑，控制表体积（删除操作低频，开销可忽略）
        deletedRowRepository.deleteByLibraryIdAndDeletedAtBefore(
                libraryId, deletedAt.minus(TOMBSTONE_RETENTION_DAYS, java.time.temporal.ChronoUnit.DAYS));
    }

    private interface BulkDeleter {
        void delete(String libraryId, List<String> ids);
    }

    private interface BulkSoftDeleter {
        void softDelete(String libraryId, List<String> ids, Long deletedAtMillis, Instant updatedAt);
    }

    private void deleteIfPresent(String libraryId, List<String> ids, BulkDeleter deleter) {
        if (ids != null && !ids.isEmpty()) {
            deleter.delete(libraryId, ids);
        }
    }

    private void softDeleteIfPresent(String libraryId, List<String> ids, Instant updatedAt, Long deletedAtMillis, BulkSoftDeleter deleter) {
        if (ids != null && !ids.isEmpty()) {
            deleter.softDelete(libraryId, ids, deletedAtMillis, updatedAt);
        }
    }

    // -----------------------------------------------------------------------
    // Query server changes since a given instant (FR-14: parallel via virtual threads)
    // -----------------------------------------------------------------------

    private LedgerChangesDto queryChangesSince(String libraryId, Instant since) {
        // 顺序查询，避免虚拟线程在 @Transactional 内部导致事务上下文丢失和异常类型被包装为 RuntimeException。
        // 原始 DataAccessException 会直接传播，GlobalExceptionHandler 可正确分类处理。
        var books = bookRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        var categories = categoryRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        var accounts = accountRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        var transactions = transactionRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        var budgets = budgetRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        var categoryBudgets = categoryBudgetRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        var deletedItems = deletedItemRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        var recurringRules = recurringRuleRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);
        var recurringOccurrences = recurringOccurrenceRepository.findByLibraryIdAndUpdatedAtAfter(libraryId, since);

        // V51: 下发 since 之后记录的删除墓碑，客户端 deleteSyncRows 据此清理本地副本
        var tombstones = deletedRowRepository.findByLibraryIdAndDeletedAtAfter(libraryId, since);
        var deletedRowIds = tombstones.stream()
                .map(t -> new DeletedRowRef(t.getTableName(), t.getRowId()))
                .toList();

        return new LedgerChangesDto(
                books.stream().map(this::bookToRow).toList(),
                categories.stream().map(this::categoryToRow).toList(),
                accounts.stream().map(this::accountToRow).toList(),
                transactions.stream().map(this::transactionToRow).toList(),
                budgets.stream().map(this::budgetToRow).toList(),
                categoryBudgets.stream().map(this::categoryBudgetToRow).toList(),
                deletedItems.stream().map(this::deletedItemToRow).toList(),
                recurringRules.stream().map(this::recurringRuleToRow).toList(),
                recurringOccurrences.stream().map(this::recurringOccurrenceToRow).toList(),
                deletedRowIds
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
        entity.setDeletedAtMillis(row.deletedAtMillis());
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
        entity.setDeletedAtMillis(row.deletedAtMillis());
        entity.setOwnerUserId(row.ownerUserId());
        entity.setBankKey(row.bankKey());
        entity.setBankName(row.bankName());
        entity.setCardNumberTail(row.cardNumberTail());
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
        entity.setDeletedAtMillis(row.deletedAtMillis());
    }

    private void mapToCategoryBudget(CategoryBudgetRow row, LedgerCategoryBudgetEntity entity) {
        entity.setBudgetId(row.budgetId());
        entity.setCategoryId(row.categoryId());
        entity.setAmountCents(row.amountCents());
        entity.setDeletedAtMillis(row.deletedAtMillis());
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
        entity.setDeletedAtMillis(row.deletedAtMillis());
    }

    private void mapToRecurringOccurrence(RecurringOccurrenceRow row, LedgerRecurringOccurrenceEntity entity) {
        entity.setRuleId(row.ruleId());
        entity.setTransactionId(row.transactionId());
        entity.setOccurrenceAtMillis(row.occurrenceAtMillis());
        entity.setDeletedAtMillis(row.deletedAtMillis());
    }

    // -----------------------------------------------------------------------
    // Entity → typed ChangeRow mappers (output, FR-10: replaces Map<String,Object>)
    // -----------------------------------------------------------------------

    private BookChangeRow bookToRow(LedgerBookEntity e) {
        return new BookChangeRow(
                e.getId(),
                e.getLibraryId(),
                e.getName(),
                e.getCreatorUserId(),
                e.getTemplate(),
                e.getCurrencyCode(),
                e.getCurrencySymbol(),
                e.getCoverColor(),
                e.getSortOrder(),
                e.getIsDeleted(),
                instantToMillis(e.getCreatedAt()),
                instantToMillis(e.getUpdatedAt())
        );
    }

    private CategoryChangeRow categoryToRow(LedgerCategoryEntity e) {
        return new CategoryChangeRow(
                e.getId(),
                e.getLibraryId(),
                e.getBookId(),
                e.getName(),
                e.getIconKey(),
                e.getColor(),
                enumName(e.getType()),
                e.getSortOrder(),
                e.getHidden(),
                e.getDeletedAtMillis(),
                instantToMillis(e.getCreatedAt()),
                instantToMillis(e.getUpdatedAt())
        );
    }

    private AccountChangeRow accountToRow(LedgerAccountEntity e) {
        return new AccountChangeRow(
                e.getId(),
                e.getLibraryId(),
                e.getBookId(),
                e.getName(),
                enumName(e.getType()),
                e.getIconKey(),
                e.getColor(),
                e.getInitialBalanceCents(),
                e.getBalanceCents(),
                e.getCreditLimitCents(),
                e.getIncludeInTotal(),
                e.getHidden(),
                e.getNote(),
                e.getSortOrder(),
                e.getDeletedAtMillis(),
                instantToMillis(e.getCreatedAt()),
                instantToMillis(e.getUpdatedAt()),
                e.getOwnerUserId(),
                e.getBankKey(),
                e.getBankName(),
                e.getCardNumberTail()
        );
    }

    private TransactionChangeRow transactionToRow(LedgerTransactionEntity e) {
        return new TransactionChangeRow(
                e.getId(),
                e.getLibraryId(),
                e.getBookId(),
                e.getCategoryId(),
                e.getAccountId(),
                e.getToAccountId(),
                e.getAmountCents(),
                enumName(e.getType()),
                e.getOccurredAtMillis(),
                e.getRemark(),
                e.getMethod(),
                e.getDeletedAtMillis(),
                instantToMillis(e.getCreatedAt()),
                instantToMillis(e.getUpdatedAt())
        );
    }

    private BudgetChangeRow budgetToRow(LedgerBudgetEntity e) {
        return new BudgetChangeRow(
                e.getId(),
                e.getLibraryId(),
                e.getBookId(),
                enumName(e.getPeriod()),
                e.getStartMillis(),
                e.getEndMillis(),
                e.getTotalAmountCents(),
                e.getDeletedAtMillis(),
                instantToMillis(e.getCreatedAt()),
                instantToMillis(e.getUpdatedAt())
        );
    }

    private CategoryBudgetChangeRow categoryBudgetToRow(LedgerCategoryBudgetEntity e) {
        // FR-10: bug fix — original categoryBudgetToMap omitted deletedAtMillis; type化 adds it.
        return new CategoryBudgetChangeRow(
                e.getId(),
                e.getLibraryId(),
                e.getBudgetId(),
                e.getCategoryId(),
                e.getAmountCents(),
                e.getDeletedAtMillis(),
                instantToMillis(e.getCreatedAt()),
                instantToMillis(e.getUpdatedAt())
        );
    }

    private DeletedItemChangeRow deletedItemToRow(LedgerDeletedItemEntity e) {
        return new DeletedItemChangeRow(
                e.getId(),
                e.getLibraryId(),
                e.getBookId(),
                e.getItemId(),
                enumName(e.getType()),
                e.getTitle(),
                e.getAmountCents(),
                e.getDeletedAtMillis(),
                e.getExpiresAtMillis(),
                instantToMillis(e.getCreatedAt()),
                instantToMillis(e.getUpdatedAt())
        );
    }

    private RecurringRuleChangeRow recurringRuleToRow(LedgerRecurringRuleEntity e) {
        return new RecurringRuleChangeRow(
                e.getId(),
                e.getLibraryId(),
                e.getBookId(),
                enumName(e.getType()),
                e.getCategoryId(),
                e.getAccountId(),
                e.getToAccountId(),
                e.getAmountCents(),
                e.getRemark(),
                enumName(e.getFrequency()),
                e.getStartAtMillis(),
                e.getEndAtMillis(),
                e.getNextOccurrenceAtMillis(),
                e.getEnabled(),
                e.getDeletedAtMillis(),
                instantToMillis(e.getCreatedAt()),
                instantToMillis(e.getUpdatedAt())
        );
    }

    private RecurringOccurrenceChangeRow recurringOccurrenceToRow(LedgerRecurringOccurrenceEntity e) {
        return new RecurringOccurrenceChangeRow(
                e.getId(),
                e.getLibraryId(),
                e.getRuleId(),
                e.getTransactionId(),
                e.getOccurrenceAtMillis(),
                e.getDeletedAtMillis(),
                instantToMillis(e.getCreatedAt()),
                instantToMillis(e.getUpdatedAt())
        );
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
}
