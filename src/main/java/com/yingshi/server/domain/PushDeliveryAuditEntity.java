package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "push_delivery_audits")
public class PushDeliveryAuditEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(nullable = false, length = 255)
    private String actorUserId;

    @Column(nullable = false, length = 40)
    private String module;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(nullable = false, length = 120)
    private String reason;

    @Column(nullable = false, length = 255)
    private String targetRoute;

    @Column(nullable = false)
    private Integer enabledDeviceCount;

    @Column(nullable = false)
    private Integer partnerDeviceCount;

    @Column(nullable = false)
    private Integer targetDeviceCount;

    @Column(nullable = false)
    private Integer attemptedCount;

    @Column(nullable = false)
    private Integer successfulCount;

    @Column(nullable = false)
    private Integer invalidTokenCount;

    @Column(nullable = false)
    private Boolean usedSelfFallback;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(String actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getTargetRoute() {
        return targetRoute;
    }

    public void setTargetRoute(String targetRoute) {
        this.targetRoute = targetRoute;
    }

    public Integer getEnabledDeviceCount() {
        return enabledDeviceCount;
    }

    public void setEnabledDeviceCount(Integer enabledDeviceCount) {
        this.enabledDeviceCount = enabledDeviceCount;
    }

    public Integer getPartnerDeviceCount() {
        return partnerDeviceCount;
    }

    public void setPartnerDeviceCount(Integer partnerDeviceCount) {
        this.partnerDeviceCount = partnerDeviceCount;
    }

    public Integer getTargetDeviceCount() {
        return targetDeviceCount;
    }

    public void setTargetDeviceCount(Integer targetDeviceCount) {
        this.targetDeviceCount = targetDeviceCount;
    }

    public Integer getAttemptedCount() {
        return attemptedCount;
    }

    public void setAttemptedCount(Integer attemptedCount) {
        this.attemptedCount = attemptedCount;
    }

    public Integer getSuccessfulCount() {
        return successfulCount;
    }

    public void setSuccessfulCount(Integer successfulCount) {
        this.successfulCount = successfulCount;
    }

    public Integer getInvalidTokenCount() {
        return invalidTokenCount;
    }

    public void setInvalidTokenCount(Integer invalidTokenCount) {
        this.invalidTokenCount = invalidTokenCount;
    }

    public Boolean getUsedSelfFallback() {
        return usedSelfFallback;
    }

    public void setUsedSelfFallback(Boolean usedSelfFallback) {
        this.usedSelfFallback = usedSelfFallback;
    }
}
