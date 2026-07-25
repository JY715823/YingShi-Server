package com.yingshi.server.dto.ledger;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * 账本同步响应。
 *
 * <p>新增 {@code rejectedRowIds} 字段（P2 修复）：服务端在 FK 预检中跳过的 row
 * 必须显式反馈给客户端，避免客户端误删 changelog 导致数据永久丢失。
 * 客户端收到 rejectedRowIds 后，应保留对应 changelog 以便下次重试，
 * 或在 UI 提示用户"部分变更未同步"。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LedgerSyncResponse(
        long versionMillis,
        LedgerChangesDto changes,
        List<RejectedRowRef> rejectedRowIds
) {
    public LedgerSyncResponse(long versionMillis, LedgerChangesDto changes) {
        this(versionMillis, changes, null);
    }

    /**
     * 被服务端拒绝的 row 引用。
     *
     * @param table 表名（books/accounts/categories/transactions 等）
     * @param id row 主键（可能为 null，如 row.id 为 null 的场景）
     * @param reason 拒绝原因（如 "blank_book_id"、"book_not_found:book-daily"）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RejectedRowRef(
            String table,
            String id,
            String reason
    ) {
    }
}
