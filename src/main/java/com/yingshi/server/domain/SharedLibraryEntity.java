package com.yingshi.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "shared_libraries")
public class SharedLibraryEntity extends BaseEntity {

    @Id
    private String id;

    @Column(nullable = false, length = 120)
    private String displayName;

    // R2-F-3: shared library 固定时区，life today/history/delete-latest 都基于此时区计算"今天"
    // 默认 Asia/Shanghai（现有库都是中国用户），由 V46 迁移回填
    @Column(name = "zone_id", nullable = false, length = 64)
    private String zoneId = "Asia/Shanghai";

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }
}
