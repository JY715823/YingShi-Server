package com.yingshi.server;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Ledger sync integration tests against real PostgreSQL.
 * Covers: full sync, incremental sync, idempotency, delete path, soft-delete cross-device propagation.
 *
 * <p>FR-4: JSON contract corrected to match DTO — top-level {@code changes} (not
 * {@code clientChanges}), nested {@code deletedRowIds: []} (not top-level {@code {}}).</p>
 *
 * Note: Ledger tables require Flyway V21+. Tests verify the sync endpoint
 * behavior when ledger tables exist (Flyway migrates to V35 in test profile).
 */
class LedgerSyncIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMPTY_SYNC_JSON = """
            {"lastSyncVersionMillis":0,"changes":{
              "books":[],"categories":[],"accounts":[],"transactions":[],
              "budgets":[],"categoryBudgets":[],"deletedItems":[],
              "recurringRules":[],"recurringOccurrences":[],
              "deletedRowIds":[]}}
            """;

    @Test
    void emptySyncReturnsEmptyChanges() throws Exception {
        String token = loginAndGetAccessToken();

        mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_SYNC_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionMillis").isNumber());
    }

    @Test
    void upsertBookAndSyncBack() throws Exception {
        String token = loginAndGetAccessToken();
        long now = System.currentTimeMillis();

        // Upsert a book
        String bookId = "book_test_" + now;
        mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastSyncVersionMillis":0,"changes":{
                                  "books":[{"id":"%s","name":"Test Book","currencyCode":"CNY"}],
                                  "categories":[],"accounts":[],"transactions":[],
                                  "budgets":[],"categoryBudgets":[],"deletedItems":[],
                                  "recurringRules":[],"recurringOccurrences":[],
                                  "deletedRowIds":[]}}
                                """.formatted(bookId)))
                .andExpect(status().isOk());

        // Second sync with lastSyncVersion=0 should return the book
        MvcResult secondSync = mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_SYNC_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Server should return the book we just upserted
        String response = secondSync.getResponse().getContentAsString();
        assert response.contains("\"books\"") : "Response should contain books array";
        assert response.contains("Test Book") : "Server should return upserted book";
    }

    @Test
    void incrementalSyncOnlyReturnsChangesSinceVersion() throws Exception {
        String token = loginAndGetAccessToken();

        // First sync to get current version
        MvcResult first = mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_SYNC_JSON))
                .andExpect(status().isOk())
                .andReturn();
        long version = Long.parseLong(readField(first, "data.versionMillis"));

        // Incremental sync from current version should return empty changes (FR-19: syncEnd
        // taken after queryChangesSince, so rows written in this request have updatedAt <= version)
        MvcResult incremental = mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_SYNC_JSON.replace("\"lastSyncVersionMillis\":0",
                                "\"lastSyncVersionMillis\":" + version)))
                .andExpect(status().isOk())
                .andReturn();
        String incResponse = incremental.getResponse().getContentAsString();
        // No new rows should be returned in incremental sync
        assert !incResponse.contains("Test Book") : "Incremental sync should not re-return old rows";
    }

    @Test
    void crossUserSyncSharesData() throws Exception {
        String tokenA = loginAndGetAccessToken(ACCOUNT_A, TEMP_PASSWORD);
        String tokenB = loginAndGetAccessToken(ACCOUNT_B, TEMP_PASSWORD);
        long now = System.currentTimeMillis();
        String bookId = "book_cross_" + now;

        // User A creates a book
        mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastSyncVersionMillis":0,"changes":{
                                  "books":[{"id":"%s","name":"Cross-User Book","currencyCode":"CNY"}],
                                  "categories":[],"accounts":[],"transactions":[],
                                  "budgets":[],"categoryBudgets":[],"deletedItems":[],
                                  "recurringRules":[],"recurringOccurrences":[],
                                  "deletedRowIds":[]}}
                                """.formatted(bookId)))
                .andExpect(status().isOk());

        // User B should see it via sync
        MvcResult syncB = mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_SYNC_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String response = syncB.getResponse().getContentAsString();
        assert response.contains("Cross-User Book") : "User B should see User A's book via shared library";
    }

    /**
     * FR-1 + FR-3: delete path uses snake_case table names and soft-deletes (sets
     * deletedAtMillis + updatedAt). The soft-deleted row is still returned on next sync
     * because queryChangesSince does NOT filter on deletedAtMillis — it propagates via
     * updatedAt > since.
     */
    @Test
    void deleteSyncPathWorks() throws Exception {
        String token = loginAndGetAccessToken();
        long now = System.currentTimeMillis();
        String categoryId = "cat_del_" + now;

        // 1. Upsert a category
        mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastSyncVersionMillis":0,"changes":{
                                  "books":[],
                                  "categories":[{"id":"%s","bookId":"book_test_%d","name":"DelCat","iconKey":"cat","type":"EXPENSE","sortOrder":0,"hidden":false}],
                                  "accounts":[],"transactions":[],"budgets":[],"categoryBudgets":[],"deletedItems":[],"recurringRules":[],"recurringOccurrences":[],
                                  "deletedRowIds":[]}}
                                """.formatted(categoryId, now)))
                .andExpect(status().isOk());

        // 2. Delete the category via deletedRowIds (FR-1: snake_case "categories")
        mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastSyncVersionMillis":0,"changes":{
                                  "books":[],"categories":[],"accounts":[],"transactions":[],"budgets":[],"categoryBudgets":[],"deletedItems":[],"recurringRules":[],"recurringOccurrences":[],
                                  "deletedRowIds":[{"table":"categories","id":"%s"}]}}
                                """.formatted(categoryId)))
                .andExpect(status().isOk());

        // 3. Sync back — category should have deletedAtMillis set (FR-3: soft delete, not hard delete)
        MvcResult result = mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_SYNC_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String response = result.getResponse().getContentAsString();
        assert response.contains("DelCat") : "Soft-deleted category should still be returned (updatedAt > since)";
        assert response.contains("deletedAtMillis") : "Category should have deletedAtMillis set after soft delete";
    }

    /**
     * FR-3: soft delete propagates across devices. User A deletes an account, User B
     * syncs and should see the account with deletedAtMillis set so the Android client
     * can filter it out from business queries.
     */
    @Test
    void softDeletePropagatesAcrossDevices() throws Exception {
        String tokenA = loginAndGetAccessToken(ACCOUNT_A, TEMP_PASSWORD);
        String tokenB = loginAndGetAccessToken(ACCOUNT_B, TEMP_PASSWORD);
        long now = System.currentTimeMillis();
        String accountId = "acc_soft_" + now;

        // 1. User A creates an account
        mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastSyncVersionMillis":0,"changes":{
                                  "books":[],"categories":[],
                                  "accounts":[{"id":"%s","bookId":"book_cross_%d","name":"SoftAcc","type":"CASH","iconKey":"cash","initialBalanceCents":0,"balanceCents":0,"includeInTotal":true,"hidden":false,"sortOrder":0}],
                                  "transactions":[],"budgets":[],"categoryBudgets":[],"deletedItems":[],"recurringRules":[],"recurringOccurrences":[],
                                  "deletedRowIds":[]}}
                                """.formatted(accountId, now)))
                .andExpect(status().isOk());

        // 2. User A deletes the account (FR-1: snake_case "accounts", FR-3: soft delete)
        MvcResult deleteResult = mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastSyncVersionMillis":0,"changes":{
                                  "books":[],"categories":[],"accounts":[],"transactions":[],"budgets":[],"categoryBudgets":[],"deletedItems":[],"recurringRules":[],"recurringOccurrences":[],
                                  "deletedRowIds":[{"table":"accounts","id":"%s"}]}}
                                """.formatted(accountId)))
                .andExpect(status().isOk())
                .andReturn();
        long version1 = Long.parseLong(readField(deleteResult, "data.versionMillis"));

        // 3. User B syncs from version 0 — should see the account with deletedAtMillis set
        MvcResult syncB = mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_SYNC_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String response = syncB.getResponse().getContentAsString();
        assert response.contains("SoftAcc") : "User B should see the soft-deleted account";
        assert response.contains("deletedAtMillis") : "Account should have deletedAtMillis set for cross-device propagation";
    }

    /**
     * FR-11/AC-5: oversized input (name > 255) must be rejected with 400 Bad Request
     * (not 500 Internal Server Error). Bean Validation @Size on LedgerSyncRows.BookRow.name
     * triggers via @Valid cascade: Controller -> Request -> Dto -> Row.
     */
    @Test
    void oversizedInputReturns400() throws Exception {
        String token = loginAndGetAccessToken();
        String oversizedName = "x".repeat(256); // max=255, so 256 must fail

        mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastSyncVersionMillis":0,"changes":{
                                  "books":[{"id":"book_oversize","name":"%s","currencyCode":"CNY"}],
                                  "categories":[],"accounts":[],"transactions":[],
                                  "budgets":[],"categoryBudgets":[],"deletedItems":[],
                                  "recurringRules":[],"recurringOccurrences":[],
                                  "deletedRowIds":[]}}
                                """.formatted(oversizedName)))
                .andExpect(status().isBadRequest());
    }

    /**
     * FR-11/AC-4: normal-length input is accepted (200 OK), confirming @Size does not
     * reject valid payloads.
     */
    @Test
    void normalSizeInputAccepted() throws Exception {
        String token = loginAndGetAccessToken();
        long now = System.currentTimeMillis();
        String bookId = "book_normal_" + now;

        mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastSyncVersionMillis":0,"changes":{
                                  "books":[{"id":"%s","name":"Normal Book","currencyCode":"CNY"}],
                                  "categories":[],"accounts":[],"transactions":[],
                                  "budgets":[],"categoryBudgets":[],"deletedItems":[],
                                  "recurringRules":[],"recurringOccurrences":[],
                                  "deletedRowIds":[]}}
                                """.formatted(bookId)))
                .andExpect(status().isOk());
    }

    /**
     * FR-1/AC-1~AC-4: delete path works for the 4 snake_case table names that were
     * previously broken by camelCase mismatch (category_budgets, deleted_items,
     * recurring_rules, recurring_occurrences). Before FR-1, the server used camelCase
     * constants (e.g. "categoryBudgets") which never matched the snake_case keys sent
     * by the client, causing these 4 delete paths to silently no-op.
     *
     * <p>This test verifies the TABLE_* constant fix by exercising each affected table:
     * <ul>
     *   <li>FR-1/AC-1: category_budgets soft-deleted → returned with deletedAtMillis</li>
     *   <li>FR-1/AC-2: deleted_items hard-deleted → NOT returned</li>
     *   <li>FR-1/AC-3: recurring_rules soft-deleted → returned with deletedAtMillis</li>
     *   <li>FR-1/AC-4: recurring_occurrences soft-deleted → returned with deletedAtMillis</li>
     * </ul>
     * Note: "categories" and "accounts" (tested in deleteSyncPathWorks / softDeletePropagatesAcrossDevices)
     * are single-word tables where camelCase == snake_case, so they never had the mismatch bug.
     * This test specifically covers the 4 compound-word tables that FR-1 fixed.</p>
     */
    @Test
    void deletePathForSnakeCaseTables() throws Exception {
        String token = loginAndGetAccessToken();
        long now = System.currentTimeMillis();
        long endMillis = now + 2592000000L; // +30 days
        String bookId = "book_snake_" + now;
        String categoryId = "cat_snake_" + now;
        String budgetId = "bud_snake_" + now;
        String categoryBudgetId = "cb_snake_" + now;
        String ruleId = "rule_snake_" + now;
        String occurrenceId = "occ_snake_" + now;
        String deletedItemId = "di_snake_" + now;

        // 1. Upsert all required entities (book → category/budget → category_budget;
        //    book → recurring_rule → recurring_occurrence; book → deleted_item)
        mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastSyncVersionMillis":0,"changes":{
                                  "books":[{"id":"%s","name":"Snake Book","currencyCode":"CNY"}],
                                  "categories":[{"id":"%s","bookId":"%s","name":"SnakeCat","iconKey":"cat","type":"EXPENSE","sortOrder":0,"hidden":false}],
                                  "accounts":[],"transactions":[],
                                  "budgets":[{"id":"%s","bookId":"%s","period":"MONTHLY","startMillis":%d,"endMillis":%d,"totalAmountCents":100000}],
                                  "categoryBudgets":[{"id":"%s","budgetId":"%s","categoryId":"%s","amountCents":50000}],
                                  "deletedItems":[{"id":"%s","bookId":"%s","itemId":"item_snake","type":"TRANSACTION","title":"DeletedTx","amountCents":1000,"deletedAtMillis":%d,"expiresAtMillis":%d}],
                                  "recurringRules":[{"id":"%s","bookId":"%s","type":"EXPENSE","categoryId":"%s","amountCents":2000,"remark":"MonthlyRent","frequency":"MONTHLY","startAtMillis":%d,"nextOccurrenceAtMillis":%d,"enabled":true}],
                                  "recurringOccurrences":[{"id":"%s","ruleId":"%s","occurrenceAtMillis":%d}],
                                  "deletedRowIds":[]}}
                                """.formatted(
                                bookId,
                                categoryId, bookId,
                                budgetId, bookId, now, endMillis,
                                categoryBudgetId, budgetId, categoryId,
                                deletedItemId, bookId, now, endMillis,
                                ruleId, bookId, categoryId, now, endMillis,
                                occurrenceId, ruleId, now)))
                .andExpect(status().isOk());

        // 2. Delete the 4 snake_case tables via deletedRowIds (FR-1: TABLE_* constants now snake_case)
        mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastSyncVersionMillis":0,"changes":{
                                  "books":[],"categories":[],"accounts":[],"transactions":[],"budgets":[],"categoryBudgets":[],"deletedItems":[],"recurringRules":[],"recurringOccurrences":[],
                                  "deletedRowIds":[
                                    {"table":"category_budgets","id":"%s"},
                                    {"table":"recurring_rules","id":"%s"},
                                    {"table":"recurring_occurrences","id":"%s"},
                                    {"table":"deleted_items","id":"%s"}
                                  ]}}
                                """.formatted(categoryBudgetId, ruleId, occurrenceId, deletedItemId)))
                .andExpect(status().isOk());

        // 3. Sync back — verify soft-deleted rows return (with deletedAtMillis via updatedAt > since),
        //    hard-deleted deleted_item is absent.
        MvcResult result = mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_SYNC_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String response = result.getResponse().getContentAsString();

        // FR-1/AC-1: category_budgets soft-deleted → still returned (updatedAt > since)
        assert response.contains(categoryBudgetId) : "Soft-deleted category_budget should still be returned";
        // FR-1/AC-3: recurring_rules soft-deleted → still returned
        assert response.contains(ruleId) : "Soft-deleted recurring_rule should still be returned";
        // FR-1/AC-4: recurring_occurrences soft-deleted → still returned
        assert response.contains(occurrenceId) : "Soft-deleted recurring_occurrence should still be returned";
        // FR-1/AC-2: deleted_items hard-deleted → NOT returned
        assert !response.contains(deletedItemId) : "Hard-deleted deleted_item should NOT be returned";
    }
}
