package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "media")
public class MediaEntity extends LibraryScopedEntity {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaType mediaType;

    @Column(nullable = false, length = 512)
    private String url;

    @Column(nullable = false, length = 512)
    private String previewUrl;

    @Column(length = 512)
    private String originalUrl;

    @Column(length = 512)
    private String videoUrl;

    @Column(length = 512)
    private String coverUrl;

    @Column(nullable = false, length = 120)
    private String mimeType;

    @Column(nullable = false)
    private Long sizeBytes;

    @Column(nullable = false)
    private Integer width;

    @Column(nullable = false)
    private Integer height;

    @Column(nullable = false)
    private Double aspectRatio;

    @Column(nullable = false)
    private Long displayTimeMillis;

    @Column
    private Long capturedAtMillis;

    @Column(nullable = false)
    private Long importedAtMillis;

    @Column(nullable = false, length = 20)
    private String displayTimeSource;

    @Column
    private Long durationMillis;

    @Column(nullable = false, length = 512)
    private String storagePath;

    @Column(length = 40)
    private String storageProvider;

    @Column(length = 120)
    private String bucket;

    @Column(length = 512)
    private String originalObjectKey;

    @Column(length = 512)
    private String previewObjectKey;

    @Column(length = 512)
    private String coverObjectKey;

    @Column(length = 128)
    private String checksum;

    @Column(length = 128)
    private String sourceFingerprint;

    @Column(length = 255)
    private String recordOwnerUserId;

    @Column(length = 255)
    private String uploadedByUserId;

    @Column(nullable = false, length = 20)
    private String domain = "photo";

    @Column
    private Instant deletedAt;

    // FR-18: Location tracking
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "location_label", length = 255)
    private String locationLabel;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
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

    public Double getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(Double aspectRatio) {
        this.aspectRatio = aspectRatio;
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

    public Long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(Long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(String storageProvider) {
        this.storageProvider = storageProvider;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getOriginalObjectKey() {
        return originalObjectKey;
    }

    public void setOriginalObjectKey(String originalObjectKey) {
        this.originalObjectKey = originalObjectKey;
    }

    public String getPreviewObjectKey() {
        return previewObjectKey;
    }

    public void setPreviewObjectKey(String previewObjectKey) {
        this.previewObjectKey = previewObjectKey;
    }

    public String getCoverObjectKey() {
        return coverObjectKey;
    }

    public void setCoverObjectKey(String coverObjectKey) {
        this.coverObjectKey = coverObjectKey;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getSourceFingerprint() {
        return sourceFingerprint;
    }

    public void setSourceFingerprint(String sourceFingerprint) {
        this.sourceFingerprint = sourceFingerprint;
    }

    public String getRecordOwnerUserId() {
        return recordOwnerUserId;
    }

    public void setRecordOwnerUserId(String recordOwnerUserId) {
        this.recordOwnerUserId = recordOwnerUserId;
    }

    public String getUploadedByUserId() {
        return uploadedByUserId;
    }

    public void setUploadedByUserId(String uploadedByUserId) {
        this.uploadedByUserId = uploadedByUserId;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
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
}
