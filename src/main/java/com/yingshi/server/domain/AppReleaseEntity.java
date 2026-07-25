package com.yingshi.server.domain;

import com.yingshi.server.common.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * App 版本发布记录。
 *
 * <p>用于客户端启动时检查更新。该表是全局表（不依赖 library_id），
 * 因为 App 包体本身不绑定任何具体 library。
 *
 * <p>发布新版本的流程：插入一条 published=true 的记录即可，
 * 客户端会按 created_at DESC 取最新一条。
 */
@Entity
@Table(name = "app_releases")
public class AppReleaseEntity extends BaseEntity {

    private static final String ID_PREFIX = "apprel_";

    @Id
    @Column(name = "id", length = 48, nullable = false, updatable = false)
    private String id;

    /** 平台：android / ios。当前仅 android。 */
    @Column(name = "platform", nullable = false, length = 16)
    private String platform;

    /** 整数版本号，用于客户端比较（来自 build.gradle.kts 的 versionCode）。 */
    @Column(name = "version_code", nullable = false)
    private Integer versionCode;

    /** 用户可见版本名（如 "1.0"、"1.1.0"，来自 build.gradle.kts 的 versionName）。 */
    @Column(name = "version_name", nullable = false, length = 32)
    private String versionName;

    /** APK 下载地址。可以是相对路径（由 Nginx 提供）或绝对 URL。 */
    @Column(name = "download_url", nullable = false, length = 512)
    private String downloadUrl;

    /** 更新说明（支持 \n 换行，客户端原样展示）。 */
    @Column(name = "update_description", length = 2000)
    private String updateDescription;

    /** 是否强制更新。true 时客户端必须更新才能继续使用。 */
    @Column(name = "force_update", nullable = false)
    private Boolean forceUpdate = false;

    /** 是否对外发布。false 时不会被检查接口返回（用于灰度/预演）。 */
    @Column(name = "published", nullable = false)
    private Boolean published = true;

    @PrePersist
    void ensureId() {
        if (id == null || id.isBlank()) {
            id = IdGenerator.newId(ID_PREFIX);
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public Integer getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(Integer versionCode) {
        this.versionCode = versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getUpdateDescription() {
        return updateDescription;
    }

    public void setUpdateDescription(String updateDescription) {
        this.updateDescription = updateDescription;
    }

    public Boolean getForceUpdate() {
        return forceUpdate;
    }

    public void setForceUpdate(Boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    public Boolean getPublished() {
        return published;
    }

    public void setPublished(Boolean published) {
        this.published = published;
    }
}
