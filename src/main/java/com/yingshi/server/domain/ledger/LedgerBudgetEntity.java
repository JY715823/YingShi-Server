package com.yingshi.server.domain.ledger;

import com.yingshi.server.domain.LibraryScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_budgets")
public class LedgerBudgetEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(name = "book_id", nullable = false)
    private String bookId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerBudgetPeriod period;

    @Column(name = "start_millis", nullable = false)
    private Long startMillis;

    @Column(name = "end_millis", nullable = false)
    private Long endMillis;

    @Column(name = "total_amount_cents", nullable = false)
    private Long totalAmountCents;

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

    public LedgerBudgetPeriod getPeriod() {
        return period;
    }

    public void setPeriod(LedgerBudgetPeriod period) {
        this.period = period;
    }

    public Long getStartMillis() {
        return startMillis;
    }

    public void setStartMillis(Long startMillis) {
        this.startMillis = startMillis;
    }

    public Long getEndMillis() {
        return endMillis;
    }

    public void setEndMillis(Long endMillis) {
        this.endMillis = endMillis;
    }

    public Long getTotalAmountCents() {
        return totalAmountCents;
    }

    public void setTotalAmountCents(Long totalAmountCents) {
        this.totalAmountCents = totalAmountCents;
    }
}
