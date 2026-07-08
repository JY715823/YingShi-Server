package com.yingshi.server.domain.ledger;

import com.yingshi.server.domain.LibraryScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_deleted_items")
public class LedgerDeletedItemEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(name = "book_id", nullable = false)
    private String bookId;

    @Column(name = "item_id", nullable = false)
    private String itemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerDeletedItemType type;

    @Column(nullable = false)
    private String title;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "deleted_at_millis", nullable = false)
    private Long deletedAtMillis;

    @Column(name = "expires_at_millis", nullable = false)
    private Long expiresAtMillis;

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

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public LedgerDeletedItemType getType() {
        return type;
    }

    public void setType(LedgerDeletedItemType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(Long amountCents) {
        this.amountCents = amountCents;
    }

    public Long getDeletedAtMillis() {
        return deletedAtMillis;
    }

    public void setDeletedAtMillis(Long deletedAtMillis) {
        this.deletedAtMillis = deletedAtMillis;
    }

    public Long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public void setExpiresAtMillis(Long expiresAtMillis) {
        this.expiresAtMillis = expiresAtMillis;
    }
}
