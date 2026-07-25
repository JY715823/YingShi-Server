package com.yingshi.server.service.ledger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Round 3 FR-7: 应用启动时触发账本数据迁移（ledger_snapshots → 9 张关系表）。
 * <p>
 * 采用独立 {@code @Configuration} + {@code @Bean ApplicationRunner} 模式，
 * 注入 {@link LedgerDataMigrationService} 代理对象后调用 {@code migrateIfNeeded()}，
 * 避免 Service 自实现 Runner 导致 {@code this.migrateIfNeeded()} 自调用绕过
 * {@code @Transactional} 代理的事务边界。
 * <p>
 * {@code migrateIfNeeded()} 内部已实现幂等检查（{@code count() > 0} 则跳过），
 * 多次启动安全。
 */
@Configuration
public class LedgerDataMigrationStartupRunner {

    private static final Logger log = LoggerFactory.getLogger(LedgerDataMigrationStartupRunner.class);

    @Bean
    @Order(30)
    ApplicationRunner ledgerDataMigrationRunner(LedgerDataMigrationService migrationService) {
        return args -> {
            log.info("Triggering ledger data migration check at startup.");
            migrationService.migrateIfNeeded();
        };
    }
}
