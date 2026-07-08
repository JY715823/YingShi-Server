package com.yingshi.server.domain.ledger;

import com.yingshi.server.domain.LibraryScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_transactions")
public class LedgerTransactionEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(name = "book_id", nullable = false)
    private String bookId;

    @Column(name = "category_id")
    private String categoryId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "to_account_id")
    private String toAccountId;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerTransactionType type;

    @Column(name = "occurred_at_millis", nullable = false)
    private Long occurredAtMillis;

    @Column(columnDefinition = "text")
    private String remark;

    private String method;

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

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getToAccountId() {
        return toAccountId;
    }

    public void setToAccountId(String toAccountId) {
        this.toAccountId = toAccountId;
    }

    public Long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(Long amountCents) {
        this.amountCents = amountCents;
    }

    public LedgerTransactionType getType() {
        return type;
    }

    public void setType(LedgerTransactionType type) {
        this.type = type;
    }

    public Long getOccurredAtMillis() {
        return occurredAtMillis;
    }

    public void setOccurredAtMillis(Long occurredAtMillis) {
        this.occurredAtMillis = occurredAtMillis;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Long getDeletedAtMillis() {
        return deletedAtMillis;
    }

    public void setDeletedAtMillis(Long deletedAtMillis) {
        this.deletedAtMillis = deletedAtMillis;
    }
}
