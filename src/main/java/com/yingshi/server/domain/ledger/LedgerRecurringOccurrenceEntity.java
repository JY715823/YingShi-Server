package com.yingshi.server.domain.ledger;

import com.yingshi.server.domain.LibraryScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_recurring_occurrences")
public class LedgerRecurringOccurrenceEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "occurrence_at_millis", nullable = false)
    private Long occurrenceAtMillis;

    @Column(name = "deleted_at_millis")
    private Long deletedAtMillis;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public Long getOccurrenceAtMillis() {
        return occurrenceAtMillis;
    }

    public void setOccurrenceAtMillis(Long occurrenceAtMillis) {
        this.occurrenceAtMillis = occurrenceAtMillis;
    }

    public Long getDeletedAtMillis() {
        return deletedAtMillis;
    }

    public void setDeletedAtMillis(Long deletedAtMillis) {
        this.deletedAtMillis = deletedAtMillis;
    }
}
