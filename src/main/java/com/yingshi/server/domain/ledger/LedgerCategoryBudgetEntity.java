package com.yingshi.server.domain.ledger;

import com.yingshi.server.domain.LibraryScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_category_budgets")
public class LedgerCategoryBudgetEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(name = "budget_id", nullable = false)
    private String budgetId;

    @Column(name = "category_id", nullable = false)
    private String categoryId;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "deleted_at_millis")
    private Long deletedAtMillis;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBudgetId() {
        return budgetId;
    }

    public void setBudgetId(String budgetId) {
        this.budgetId = budgetId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
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
}
