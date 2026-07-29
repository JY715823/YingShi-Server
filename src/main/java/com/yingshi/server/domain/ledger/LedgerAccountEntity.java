package com.yingshi.server.domain.ledger;

import com.yingshi.server.domain.LibraryScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_accounts")
public class LedgerAccountEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(name = "book_id", nullable = false)
    private String bookId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerAccountType type;

    @Column(name = "icon_key", nullable = false)
    private String iconKey;

    @Column(nullable = false)
    private Long color;

    @Column(name = "initial_balance_cents", nullable = false)
    private Long initialBalanceCents;

    @Column(name = "balance_cents", nullable = false)
    private Long balanceCents;

    @Column(name = "credit_limit_cents")
    private Long creditLimitCents;

    @Column(name = "include_in_total", nullable = false)
    private Boolean includeInTotal = true;

    @Column(nullable = false)
    private Boolean hidden = false;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "deleted_at_millis")
    private Long deletedAtMillis;

    @Column(name = "owner_user_id")
    private String ownerUserId;

    @Column(name = "bank_key", length = 64)
    private String bankKey;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "card_number_tail", length = 16)
    private String cardNumberTail;

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

    public LedgerAccountType getType() {
        return type;
    }

    public void setType(LedgerAccountType type) {
        this.type = type;
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

    public Long getInitialBalanceCents() {
        return initialBalanceCents;
    }

    public void setInitialBalanceCents(Long initialBalanceCents) {
        this.initialBalanceCents = initialBalanceCents;
    }

    public Long getBalanceCents() {
        return balanceCents;
    }

    public void setBalanceCents(Long balanceCents) {
        this.balanceCents = balanceCents;
    }

    public Long getCreditLimitCents() {
        return creditLimitCents;
    }

    public void setCreditLimitCents(Long creditLimitCents) {
        this.creditLimitCents = creditLimitCents;
    }

    public Boolean getIncludeInTotal() {
        return includeInTotal;
    }

    public void setIncludeInTotal(Boolean includeInTotal) {
        this.includeInTotal = includeInTotal;
    }

    public Boolean getHidden() {
        return hidden;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getDeletedAtMillis() {
        return deletedAtMillis;
    }

    public void setDeletedAtMillis(Long deletedAtMillis) {
        this.deletedAtMillis = deletedAtMillis;
    }

    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getBankKey() { return bankKey; }
    public void setBankKey(String bankKey) { this.bankKey = bankKey; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getCardNumberTail() { return cardNumberTail; }
    public void setCardNumberTail(String cardNumberTail) { this.cardNumberTail = cardNumberTail; }
}
