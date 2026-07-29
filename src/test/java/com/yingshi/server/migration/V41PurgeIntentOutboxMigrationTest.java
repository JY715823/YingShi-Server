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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * R0-I: V41 迁移真执行测试。
 * <p>
 * 用真实 PostgreSQL 16 容器验证 {@code V41__purge_intent_outbox.sql}：
 * <ul>
 *   <li>从 V40 基线升级到 V41 不报错</li>
 *   <li>purge_intents 表已创建，包含全部字段 (id, trash_item_id, library_id, object_type,
 *       state, attempts, max_attempts, next_retry_at, last_error, completed_at)</li>
 *   <li>三个索引已建立：state_retry (部分索引)、trash_item、library</li>
 *   <li>插入只含必填字段的记录时，state 默认 'PENDING'，attempts 默认 0，max_attempts 默认 5</li>
 *   <li>全新库跑到 V41 直接成功 (相当于首次安装)</li>
 * </ul>
 */
@Testcontainers
class V41PurgeIntentOutboxMigrationTest {

    private static final String MIGRATION_LOCATION = "classpath:db/migration/postgresql";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("yingshi_v41_test")
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
    void v41MigrationCreatesPurgeIntentsTableWithCorrectSchema() {
        migrateTo("40");
        assertThatCode(() -> migrateTo("41")).doesNotThrowAnyException();
        assertThat(appliedMigrationVersion("41")).isTrue();

        // 表已创建
        assertThat(tableExists("purge_intents")).isTrue();
        // 关键字段
        assertThat(columnExists("purge_intents", "id")).isTrue();
        assertThat(columnExists("purge_intents", "trash_item_id")).isTrue();
        assertThat(columnExists("purge_intents", "library_id")).isTrue();
        assertThat(columnExists("purge_intents", "object_type")).isTrue();
        assertThat(columnExists("purge_intents", "state")).isTrue();
        assertThat(columnExists("purge_intents", "attempts")).isTrue();
        assertThat(columnExists("purge_intents", "max_attempts")).isTrue();
        assertThat(columnExists("purge_intents", "next_retry_at")).isTrue();
        assertThat(columnExists("purge_intents", "last_error")).isTrue();
        assertThat(columnExists("purge_intents", "completed_at")).isTrue();
        // 索引
        assertThat(indexExists("idx_purge_intents_state_retry")).isTrue();
        assertThat(indexExists("idx_purge_intents_trash_item")).isTrue();
        assertThat(indexExists("idx_purge_intents_library")).isTrue();
    }

    @Test
    void v41MigrationAppliesDefaultValuesToInserts() {
        migrateTo("41");
        // 插入只含必填字段的记录, 验证默认值
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO purge_intents (id, trash_item_id, library_id, object_type) " +
                             "VALUES (?, ?, ?, ?)")) {
            ps.setString(1, "intent-1");
            ps.setString(2, "trash-1");
            ps.setString(3, "lib-1");
            ps.setString(4, "MEDIA");
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert purge_intent", e);
        }

        // 验证默认值
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT state, attempts, max_attempts FROM purge_intents WHERE id = ?")) {
            ps.setString(1, "intent-1");
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("state")).isEqualTo("PENDING");
                assertThat(rs.getInt("attempts")).isEqualTo(0);
                assertThat(rs.getInt("max_attempts")).isEqualTo(5);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void v41MigrationIsIdempotentOnFreshInstall() {
        // 全新库直接跑到 V41 应该成功 (相当于首次安装)
        assertThatCode(() -> migrateTo("41")).doesNotThrowAnyException();
        assertThat(appliedMigrationVersion("41")).isTrue();
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

    private boolean tableExists(String tableName) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM information_schema.tables WHERE table_name = ?")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
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
