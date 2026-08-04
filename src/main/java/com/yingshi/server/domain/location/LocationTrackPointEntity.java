package com.yingshi.server.domain.location;

import com.yingshi.server.domain.LibraryScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * V52: 足迹定位轨迹点。
 *
 * 客户端后台定时采样（闹钟 1h/3h + PASSIVE 搭车）后批量上行，坐标为 GCJ-02
 * （与上传媒体坐标系统一）。用途：
 * 1. 足迹地图（未来功能）；
 * 2. 上传无 GPS 媒体时按拍摄时间就近回填位置（客户端本地完成，本表做跨设备留存）。
 *
 * 唯一约束 (user_id, recorded_at) 保证客户端重传幂等。
 */
@Entity
@Table(
        name = "location_track_points",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_location_track_user_recorded",
                columnNames = {"user_id", "recorded_at"}
        )
)
public class LocationTrackPointEntity extends LibraryScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    /** 定位精度（米），可空。 */
    private Float accuracy;

    /** 采样来源：alarm=闹钟定时, passive=搭车监听, resident=常驻前台服务。 */
    @Column(nullable = false)
    private String source;

    /** 采样时刻（客户端时钟）。 */
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public Float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Float accuracy) {
        this.accuracy = accuracy;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
