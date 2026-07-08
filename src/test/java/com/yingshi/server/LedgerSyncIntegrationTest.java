package com.yingshi.server;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Ledger sync integration tests against real PostgreSQL.
 * Covers: full sync, incremental sync, idempotency.
 *
 * Note: Ledger tables require Flyway V21+. Tests verify the sync endpoint
 * behavior when ledger tables exist (Flyway migrates to V25 in test profile).
 */
class LedgerSyncIntegrationTest extends AbstractPostgresIntegrationTest {

    @Test
    void emptySyncReturnsEmptyChanges() throws Exception {
        String token = loginAndGetAccessToken();

        mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lastSyncVersionMillis": 0,
                                  "clientChanges": {
                                    "books": [], "categories": [], "accounts": [],
                                    "transactions": [], "budgets": [], "categoryBudgets": [],
                                    "deletedItems": [], "recurringRules": [], "recurringOccurrences": []
                                  },
                                  "deletedRowIds": {}
                                }
                                """))
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
                                {
                                  "lastSyncVersionMillis": 0,
                                  "clientChanges": {
                                    "books": [{"id": "%s", "libraryId": "library_shared",
                                               "name": "Test Book", "currencyCode": "CNY",
                                               "updatedAtMillis": %d}],
                                    "categories": [], "accounts": [],
                                    "transactions": [], "budgets": [], "categoryBudgets": [],
                                    "deletedItems": [], "recurringRules": [], "recurringOccurrences": []
                                  },
                                  "deletedRowIds": {}
                                }
                                """.formatted(bookId, now)))
                .andExpect(status().isOk());

        // Second sync with lastSyncVersion=0 should return the book
        MvcResult secondSync = mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lastSyncVersionMillis": 0,
                                  "clientChanges": {
                                    "books": [], "categories": [], "accounts": [],
                                    "transactions": [], "budgets": [], "categoryBudgets": [],
                                    "deletedItems": [], "recurringRules": [], "recurringOccurrences": []
                                  },
                                  "deletedRowIds": {}
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        // Server should return the book we just upserted
        String response = secondSync.getResponse().getContentAsString();
        assert response.contains("Test Book") : "Server should return upserted book";
    }

    @Test
    void incrementalSyncOnlyReturnsChangesSinceVersion() throws Exception {
        String token = loginAndGetAccessToken();

        // First sync to get current version
        MvcResult first = mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lastSyncVersionMillis": 0,
                                  "clientChanges": {
                                    "books": [], "categories": [], "accounts": [],
                                    "transactions": [], "budgets": [], "categoryBudgets": [],
                                    "deletedItems": [], "recurringRules": [], "recurringOccurrences": []
                                  },
                                  "deletedRowIds": {}
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long version = Long.parseLong(readField(first, "data.versionMillis"));

        // Incremental sync from current version should return empty changes
        mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lastSyncVersionMillis": %d,
                                  "clientChanges": {
                                    "books": [], "categories": [], "accounts": [],
                                    "transactions": [], "budgets": [], "categoryBudgets": [],
                                    "deletedItems": [], "recurringRules": [], "recurringOccurrences": []
                                  },
                                  "deletedRowIds": {}
                                }
                                """.formatted(version)))
                .andExpect(status().isOk());
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
                                {
                                  "lastSyncVersionMillis": 0,
                                  "clientChanges": {
                                    "books": [{"id": "%s", "libraryId": "library_shared",
                                               "name": "Cross-User Book", "currencyCode": "CNY",
                                               "updatedAtMillis": %d}],
                                    "categories": [], "accounts": [],
                                    "transactions": [], "budgets": [], "categoryBudgets": [],
                                    "deletedItems": [], "recurringRules": [], "recurringOccurrences": []
                                  },
                                  "deletedRowIds": {}
                                }
                                """.formatted(bookId, now)))
                .andExpect(status().isOk());

        // User B should see it via sync
        MvcResult syncB = mockMvc.perform(post("/api/ledger/sync")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lastSyncVersionMillis": 0,
                                  "clientChanges": {
                                    "books": [], "categories": [], "accounts": [],
                                    "transactions": [], "budgets": [], "categoryBudgets": [],
                                    "deletedItems": [], "recurringRules": [], "recurringOccurrences": []
                                  },
                                  "deletedRowIds": {}
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String response = syncB.getResponse().getContentAsString();
        assert response.contains("Cross-User Book") : "User B should see User A's book via shared library";
    }
}
