package com.yingshi.server.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * R0-I: V40 迁移真执行测试。
 * <p>
 * 用真实 PostgreSQL 16 容器验证 {@code V40__auth_version_fields.sql}：
 * <ul>
 *   <li>从 V39 基线升级到 V40 不报错</li>
 *   <li>auth_sessions / auth_remembered_logins 表新增 version 列 (默认 0)</li>
 *   <li>会话族撤销索引 idx_auth_sessions_user_library_revoked 已建立 (部分索引, WHERE revoked_at IS NULL)</li>
 *   <li>既有 auth_sessions 数据未丢失，version 字段被填充为 0</li>
 *   <li>全新库 (相当于首次安装) 跑到 V40 直接成功</li>
 * </ul>
 * <p>
 * 此测试不依赖 Spring 上下文，直接使用 Flyway 程序化 API + JDBC，
 * 与 {@link V24OrphanDataMigrationTest} 风格一致。
 */
@Testcontainers
class V40AuthVersionFieldsMigrationTest {

    private static final String MIGRATION_LOCATION = "classpath:db/migration/postgresql";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("yingshi_v40_test")
            .withUsername("test")
            .withPassword("test");

    @BeforeEach
    void resetSchema() {
        try (Connection conn = openConnection();
             Statement st = conn.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS public CASCADE");
            st.execute("CREATE SCHEMA public");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to reset schema", e);
        }
    }

    @Test
    void v40MigrationSucceedsAfterCleanV39Baseline() {
        migrateTo("39");
        assertThatCode(() -> migrateTo("40")).doesNotThrowAnyException();
        assertThat(appliedMigrationVersion("40")).isTrue();
        // 乐观锁列已建立
        assertThat(columnExists("auth_sessions", "version")).isTrue();
        assertThat(columnExists("auth_remembered_logins", "version")).isTrue();
        // 会话族撤销部分索引已建立
        assertThat(indexExists("idx_auth_sessions_user_library_revoked")).isTrue();
    }

    @Test
    void v40MigrationIsIdempotentOnFreshInstall() {
        // 全新库直接跑到 V40 应该成功 (相当于首次安装)
        assertThatCode(() -> migrateTo("40")).doesNotThrowAnyException();
        assertThat(appliedMigrationVersion("40")).isTrue();
    }

    @Test
    void v40MigrationPreservesExistingAuthSessions() {
        migrateTo("39");
        // V24 添加了 FK 约束：auth_sessions.user_id → users.id, auth_sessions.library_id → shared_libraries.id。
        // 必须先插入父表数据，否则 INSERT 会因 FK 违反失败。
        insertSharedLibrary("lib-1");
        insertUser("user-1", "lib-1");
        insertAuthSession("sess-1", "user-1", "lib-1");
        migrateTo("40");
        // 数据未丢失
        assertThat(rowExists("auth_sessions", "sess-1")).isTrue();
        // 乐观锁版本默认 0
        assertThat(columnLongValue("auth_sessions", "sess-1", "version")).isEqualTo(0L);
    }

    // ---- Flyway helpers ----

    private void migrateTo(String targetVersion) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations(MIGRATION_LOCATION)
                .target(MigrationVersion.fromVersion(targetVersion))
                .load()
                .migrate();
    }

    // ---- JDBC helpers ----

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private void insertAuthSession(String sessionId, String userId, String libraryId) {
        // auth_sessions 在 V4 创建时的实际列：id, created_at, updated_at, library_id,
        // user_id, refresh_token_id, refresh_expire_at, last_authenticated_at, revoked_at。
        // 注意：没有 issued_at/expires_at 列（JWT access token 是无状态的，不入库）。
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO auth_sessions (id, user_id, library_id, refresh_token_id, " +
                             "last_authenticated_at, refresh_expire_at, revoked_at, created_at, updated_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?, NULL, NOW(), NOW())")) {
            Instant now = Instant.now();
            ps.setString(1, sessionId);
            ps.setString(2, userId);
            ps.setString(3, libraryId);
            ps.setObject(4, UUID.randomUUID());
            // PostgreSQL JDBC 不能从 java.time.Instant 推断 SQL 类型，必须用 Timestamp 显式转换。
            ps.setTimestamp(5, Timestamp.from(now));
            ps.setTimestamp(6, Timestamp.from(now.plusSeconds(86400)));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert auth_session", e);
        }
    }

    private void insertSharedLibrary(String libraryId) {
        // V1 建表：id, created_at, updated_at, display_name
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO shared_libraries (id, created_at, updated_at, display_name) " +
                             "VALUES (?, NOW(), NOW(), ?)")) {
            ps.setString(1, libraryId);
            ps.setString(2, "Test Library " + libraryId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert shared_library", e);
        }
    }

    private void insertUser(String userId, String libraryId) {
        // V1 建表 + V23 failed_login_attempts/locked_until。
        // NOT NULL 列：id, created_at, updated_at, account, default_library_id, display_name, password_hash。
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (id, created_at, updated_at, account, default_library_id, " +
                             "display_name, password_hash) " +
                             "VALUES (?, NOW(), NOW(), ?, ?, ?, ?)")) {
            ps.setString(1, userId);
            ps.setString(2, "test-" + userId + "@example.com");
            ps.setString(3, libraryId);
            ps.setString(4, "Test User " + userId);
            ps.setString(5, "$2a$10$dummyhashfornonproductiontestuseonly");
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert user", e);
        }
    }

    private boolean columnExists(String table, String column) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM information_schema.columns WHERE table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean indexExists(String indexName) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM pg_indexes WHERE indexname = ?")) {
            ps.setString(1, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean rowExists(String table, String id) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM " + table + " WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private long columnLongValue(String table, String id, String column) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT " + column + " FROM " + table + " WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean appliedMigrationVersion(String version) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM flyway_schema_history WHERE version = ? AND success = true")) {
            ps.setString(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
