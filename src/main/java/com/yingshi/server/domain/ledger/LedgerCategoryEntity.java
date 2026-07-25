package com.yingshi.server.domain.ledger;

import com.yingshi.server.domain.LibraryScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_categories")
public class LedgerCategoryEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(name = "book_id", nullable = false)
    private String bookId;

    @Column(nullable = false)
    private String name;

    @Column(name = "icon_key", nullable = false)
    private String iconKey;

    @Column(nullable = false)
    private Long color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerCategoryType type;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private Boolean hidden = false;

    @Column(name = "deleted_at_millis")
    private Long deletedAtMillis;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = bookId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIconKey() {
        return iconKey;
    }

    public void setIconKey(String iconKey) {
        this.iconKey = iconKey;
    }

    public Long getColor() {
        return color;
    }

    public void setColor(Long color) {
        this.color = color;
    }

    public LedgerCategoryType getType() {
        return type;
    }

    public void setType(LedgerCategoryType type) {
        this.type = type;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getHidden() {
        return hidden;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    public Long getDeletedAtMillis() {
        return deletedAtMillis;
    }

    public void setDeletedAtMillis(Long deletedAtMillis) {
        this.deletedAtMillis = deletedAtMillis;
    }
}
