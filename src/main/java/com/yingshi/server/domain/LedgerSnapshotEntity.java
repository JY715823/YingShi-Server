package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * @deprecated 旧账本 JSON 快照实体，写入端点已移除。账本数据已迁移至 9 张关系表（V21）。
 *             读取方将在 Round 3（FR-5 SyncService / FR-9 NotificationService）切换至关系表。
 *             请勿新增对此实体的引用。
 */
@Deprecated
@Entity
@Table(
        name = "ledger_snapshots",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ledger_snapshots_library_id", columnNames = "library_id")
        }
)
public class LedgerSnapshotEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Column(name = "last_modified_by")
    private String lastModifiedByUserId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getLastModifiedByUserId() {
        return lastModifiedByUserId;
    }

    public void setLastModifiedByUserId(String lastModifiedByUserId) {
        this.lastModifiedByUserId = lastModifiedByUserId;
    }
}
