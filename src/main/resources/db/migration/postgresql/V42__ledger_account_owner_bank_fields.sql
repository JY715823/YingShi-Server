-- R1-E-1: Account 四字段（ownerUserId/bankKey/bankName/cardNumberTail）补齐
-- R1-F-4: 转账和 recurring occurrence 唯一约束保证财务幂等

-- Account 四字段
ALTER TABLE ledger_accounts ADD COLUMN IF NOT EXISTS owner_user_id VARCHAR(255);
ALTER TABLE ledger_accounts ADD COLUMN IF NOT EXISTS bank_key VARCHAR(64);
ALTER TABLE ledger_accounts ADD COLUMN IF NOT EXISTS bank_name VARCHAR(255);
ALTER TABLE ledger_accounts ADD COLUMN IF NOT EXISTS card_number_tail VARCHAR(16);

-- Recurring occurrence 唯一约束（ruleId + occurrenceAtMillis）
-- 注意：V21 已有非唯一索引 idx_ledger_recurring_occurrences_rule_id，需先 DROP 再 ADD
DROP INDEX IF EXISTS idx_ledger_recurring_occurrences_rule_id;
CREATE UNIQUE INDEX IF NOT EXISTS uk_recurring_occurrences_rule_time
    ON ledger_recurring_occurrences(rule_id, occurrence_at_millis);

-- Recurring occurrence transactionId 唯一约束
CREATE UNIQUE INDEX IF NOT EXISTS uk_recurring_occurrences_tx
    ON ledger_recurring_occurrences(transaction_id)
    WHERE transaction_id IS NOT NULL;

-- 转账 dedupe key（仅 type=TRANSFER 时约束）
-- 使用部分唯一索引，非转账类型不约束
CREATE UNIQUE INDEX IF NOT EXISTS uk_transactions_transfer_dedupe
    ON ledger_transactions(book_id, LEAST(account_id, to_account_id), GREATEST(account_id, to_account_id), occurred_at_millis, amount_cents)
    WHERE type = 'TRANSFER' AND to_account_id IS NOT NULL AND deleted_at_millis IS NULL;
