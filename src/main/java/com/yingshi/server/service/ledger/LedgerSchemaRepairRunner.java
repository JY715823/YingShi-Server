package com.yingshi.server.service.ledger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * 启动时校验并修复 ledger 关系表 schema。
 * <p>
 * dev 环境使用 H2 + ddl-auto=update，Hibernate 只能新增列/表，无法处理
 * 列类型变更或遗漏的迁移。本 Runner 兜底确保 V34（deleted_at_millis）
 * 等关键列存在，避免 sync 接口因 schema mismatch 报 500。
 * <p>
 * 幂等：列已存在时 ALTER TABLE 不会执行。
 */
@Configuration
public class LedgerSchemaRepairRunner {

    private static final Logger log = LoggerFactory.getLogger(LedgerSchemaRepairRunner.class);

    @Bean
    @Order(20)
    ApplicationRunner ledgerSchemaRepairRunner(DataSource dataSource) {
        return args -> {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            try {
                repairIfMissing(jdbc);
                log.info("Ledger schema repair check completed.");
            } catch (Exception e) {
                log.warn("Ledger schema repair check failed (non-fatal): {}", e.getMessage(), e);
            }
        };
    }

    private void repairIfMissing(JdbcTemplate jdbc) {
        // V34: deleted_at_millis for 6 tables (transactions 和 deleted_items 在 V21 已有)
        ensureColumn(jdbc, "ledger_categories", "deleted_at_millis", "BIGINT");
        ensureColumn(jdbc, "ledger_accounts", "deleted_at_millis", "BIGINT");
        ensureColumn(jdbc, "ledger_budgets", "deleted_at_millis", "BIGINT");
        ensureColumn(jdbc, "ledger_category_budgets", "deleted_at_millis", "BIGINT");
        ensureColumn(jdbc, "ledger_recurring_rules", "deleted_at_millis", "BIGINT");
        ensureColumn(jdbc, "ledger_recurring_occurrences", "deleted_at_millis", "BIGINT");
        // transactions 和 deleted_items 也要兜底
        ensureColumn(jdbc, "ledger_transactions", "deleted_at_millis", "BIGINT");

        // 确保 books 表有 is_deleted 列
        ensureColumn(jdbc, "ledger_books", "is_deleted", "BOOLEAN");
    }

    private void ensureColumn(JdbcTemplate jdbc, String table, String column, String type) {
        try {
            List<Map<String, Object>> cols = jdbc.queryForList(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ?",
                    table.toUpperCase(), column.toUpperCase()
            );
            if (cols.isEmpty()) {
                log.warn("Schema repair: adding missing column {}.{} ({})", table, column, type);
                jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            }
        } catch (Exception e) {
            log.warn("Schema repair: failed to check/add {}.{}: {}", table, column, e.getMessage());
        }
    }
}
