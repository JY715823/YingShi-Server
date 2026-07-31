package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "upload_tasks")
public class UploadTaskEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaType mediaType;

    @Column(nullable = false, length = 120)
    private String mimeType;

    @Column(nullable = false)
    private Long fileSizeBytes;

    @Column(nullable = false)
    private Integer width;

    @Column(nullable = false)
    private Integer height;

    @Column
    private Long durationMillis;

    @Column(nullable = false)
    private Long displayTimeMillis;

    @Column
    private Long capturedAtMillis;

    @Column(nullable = false)
    private Long importedAtMillis;

    @Column(nullable = false, length = 20)
    private String displayTimeSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UploadState state;

    @Column(nullable = false)
    private Instant expireAt;

    @Column
    private Instant completedAt;

    @Column(length = 512)
    private String storedPath;

    @Column(length = 255)
    private String mediaId;

    @Column(length = 128)
    private String sourceFingerprint;

    @Column(length = 255)
    private String uploadedByUserId;

    @Column(length = 255)
    private String operationId;

    @Column(length = 40)
    private String operationType;

    @Column(length = 255)
    private String operationTitle;

    @Column
    private Integer operationMediaCount;

    @Column(length = 255)
    private String sourceItemId;

    /** R3-DATA-003: Client-provided idempotency key to prevent duplicate upload token creation. */
    @Column(length = 128)
    private String idempotencyKey;

    @Column(length = 20)
    private String domain;

    // life 模块分类：PERSON / MEAL / null（非 life 上传任务）
    @Column(name = "life_category", length = 20)
    private String lifeCategory;

    @Column(length = 512)
    private String errorMessage;

    @Column
    private Instant dismissedAt;

    // FR-18: Location tracking (transferred to MediaEntity on build)
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "location_label", length = 255)
    private String locationLabel;

    // V49: 全部EXIF元数据（24个字段），客户端提取后发送，传递给MediaEntity
    @Column(name = "exif_metadata", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private java.util.Map<String, Object> exifMetadata;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(Long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public Long getDisplayTimeMillis() {
        return displayTimeMillis;
    }

    public void setDisplayTimeMillis(Long displayTimeMillis) {
        this.displayTimeMillis = displayTimeMillis;
    }

    public Long getCapturedAtMillis() {
        return capturedAtMillis;
    }

    public void setCapturedAtMillis(Long capturedAtMillis) {
        this.capturedAtMillis = capturedAtMillis;
    }

    public Long getImportedAtMillis() {
        return importedAtMillis;
    }

    public void setImportedAtMillis(Long importedAtMillis) {
        this.importedAtMillis = importedAtMillis;
    }

    public String getDisplayTimeSource() {
        return displayTimeSource;
    }

    public void setDisplayTimeSource(String displayTimeSource) {
        this.displayTimeSource = displayTimeSource;
    }

    public UploadState getState() {
        return state;
    }

    public void setState(UploadState state) {
        this.state = state;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Instant expireAt) {
        this.expireAt = expireAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public void setStoredPath(String storedPath) {
        this.storedPath = storedPath;
    }

    public String getMediaId() {
        return mediaId;
    }

    public void setMediaId(String mediaId) {
        this.mediaId = mediaId;
    }

    public String getSourceFingerprint() {
        return sourceFingerprint;
    }

    public void setSourceFingerprint(String sourceFingerprint) {
        this.sourceFingerprint = sourceFingerprint;
    }

    public String getUploadedByUserId() {
        return uploadedByUserId;
    }

    public void setUploadedByUserId(String uploadedByUserId) {
        this.uploadedByUserId = uploadedByUserId;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getOperationTitle() {
        return operationTitle;
    }

    public void setOperationTitle(String operationTitle) {
        this.operationTitle = operationTitle;
    }

    public Integer getOperationMediaCount() {
        return operationMediaCount;
    }

    public void setOperationMediaCount(Integer operationMediaCount) {
        this.operationMediaCount = operationMediaCount;
    }

    public String getSourceItemId() {
        return sourceItemId;
    }

    public void setSourceItemId(String sourceItemId) {
        this.sourceItemId = sourceItemId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getLifeCategory() {
        return lifeCategory;
    }

    public void setLifeCategory(String lifeCategory) {
        this.lifeCategory = lifeCategory;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getDismissedAt() {
        return dismissedAt;
    }

    public void setDismissedAt(Instant dismissedAt) {
        this.dismissedAt = dismissedAt;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getLocationLabel() {
        return locationLabel;
    }

    public void setLocationLabel(String locationLabel) {
        this.locationLabel = locationLabel;
    }

    public java.util.Map<String, Object> getExifMetadata() {
        return exifMetadata;
    }

    public void setExifMetadata(java.util.Map<String, Object> exifMetadata) {
        this.exifMetadata = exifMetadata;
    }
}
