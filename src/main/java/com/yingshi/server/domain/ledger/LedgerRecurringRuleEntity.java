package com.yingshi.server.domain.ledger;

import com.yingshi.server.domain.LibraryScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_recurring_rules")
public class LedgerRecurringRuleEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(name = "book_id", nullable = false)
    private String bookId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerTransactionType type;

    @Column(name = "category_id")
    private String categoryId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "to_account_id")
    private String toAccountId;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(columnDefinition = "text")
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerRecurringFrequency frequency;

    @Column(name = "start_at_millis", nullable = false)
    private Long startAtMillis;

    @Column(name = "end_at_millis")
    private Long endAtMillis;

    @Column(name = "next_occurrence_at_millis", nullable = false)
    private Long nextOccurrenceAtMillis;

    @Column(nullable = false)
    private Boolean enabled = true;

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

    public LedgerTransactionType getType() {
        return type;
    }

    public void setType(LedgerTransactionType type) {
        this.type = type;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LedgerRecurringFrequency getFrequency() {
        return frequency;
    }

    public void setFrequency(LedgerRecurringFrequency frequency) {
        this.frequency = frequency;
    }

    public Long getStartAtMillis() {
        return startAtMillis;
    }

    public void setStartAtMillis(Long startAtMillis) {
        this.startAtMillis = startAtMillis;
    }

    public Long getEndAtMillis() {
        return endAtMillis;
    }

    public void setEndAtMillis(Long endAtMillis) {
        this.endAtMillis = endAtMillis;
    }

    public Long getNextOccurrenceAtMillis() {
        return nextOccurrenceAtMillis;
    }

    public void setNextOccurrenceAtMillis(Long nextOccurrenceAtMillis) {
        this.nextOccurrenceAtMillis = nextOccurrenceAtMillis;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
