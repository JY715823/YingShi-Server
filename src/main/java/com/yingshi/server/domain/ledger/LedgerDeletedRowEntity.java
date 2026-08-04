package com.yingshi.server.domain.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * V51: 账本同步删除日志（tombstone）。
 *
 * 记录每一次通过 sync deletedRowIds 执行的删除，使其他客户端在后续同步时
 * 通过 queryChangesSince 收到删除指令并清理本地副本。30 天前的条目会被清理。
 */
@Entity
@Table(name = "ledger_deleted_rows")
public class LedgerDeletedRowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "library_id", nullable = false)
    private String libraryId;

    @Column(name = "table_name", nullable = false)
    private String tableName;

    @Column(name = "row_id", nullable = false)
    private String rowId;

    @Column(name = "deleted_at", nullable = false)
    private Instant deletedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLibraryId() {
        return libraryId;
    }

    public void setLibraryId(String libraryId) {
        this.libraryId = libraryId;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getRowId() {
        return rowId;
    }

    public void setRowId(String rowId) {
        this.rowId = rowId;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
