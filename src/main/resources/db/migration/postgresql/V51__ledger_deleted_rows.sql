-- V51: 账本同步删除日志（tombstone）
--
-- 背景：账本同步协议为 last-write-wins 整行 upsert + deletedRowIds 硬删除。
-- 此前服务端删除行后不做任何记录，queryChangesSince 对 deletedRowIds 恒返回空，
-- 导致一个账号删除的行（如账本）永远不会传播到其他账号的客户端，
-- 本地副本残留并在下次 hydrate 后"复活"。本表记录每一次删除，
-- 使其他客户端在下一次同步时收到删除指令（客户端 deleteSyncRows 执行本地清理）。

create table if not exists ledger_deleted_rows (
    id bigserial primary key,
    library_id varchar(255) not null,
    table_name varchar(64) not null,
    row_id varchar(255) not null,
    deleted_at timestamptz not null default now()
);

create index if not exists idx_ledger_deleted_rows_library_deleted_at
    on ledger_deleted_rows (library_id, deleted_at);

-- 删除账本会级联删除其账户，而 ledger_transactions.account_id 的外键是 ON DELETE SET NULL，
-- 与 NOT NULL 约束矛盾：只要其他账本的账单引用了该账户，级联删除就会 23502 失败。
-- 放开 NOT NULL（与外键 SET NULL 语义对齐）——账户被删后账单保留、账户置空。
-- 这一步同时保护运行时 applyDeletions 的账本硬删除路径。
alter table ledger_transactions alter column account_id drop not null;

-- 归档语义已被"删除"取代：清理历史上被归档的账本（含三个客户端种子的默认账本
-- book-daily / book-travel / book-shared），并记录墓碑供客户端同步删除本地副本。
-- 子表（categories/accounts/transactions 等）由 V24 的 ON DELETE CASCADE 一并清理，
-- 客户端侧收到 books 删除指令时按 bookId 级联清理本地子表。
insert into ledger_deleted_rows (library_id, table_name, row_id, deleted_at)
select library_id, 'books', id, now()
from ledger_books
where is_deleted = true;

delete from ledger_books where is_deleted = true;
