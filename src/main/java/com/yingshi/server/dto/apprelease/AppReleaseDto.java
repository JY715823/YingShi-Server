package com.yingshi.server.dto.apprelease;

import com.yingshi.server.domain.AppReleaseEntity;

import java.time.Instant;

/**
 * R2-G-12: App 版本发布 DTO，用于 admin 发布端点的响应。
 *
 * <p>包含发布记录的完整信息：版本号、下载地址、SHA-256、签名指纹等。
 * 客户端可用于校验下载完整性和签名一致性。
 *
 * @param id                      发布记录 ID
 * @param platform                平台标识（android/ios）
 * @param versionCode             整数版本号
 * @param versionName             用户可见版本名
 * @param downloadUrl             APK 下载地址（相对路径或绝对 URL）
 * @param downloadHost            APK 下载的 CDN/对象存储域名
 * @param updateDescription       更新说明
 * @param forceUpdate             是否强制更新
 * @param published               是否对外发布
 * @param apkSha256               APK 文件 SHA-256 校验值
 * @param fileSizeBytes           APK 文件大小（字节）
 * @param minSupportedVersionCode 支持的最低客户端 versionCode
 * @param signerSha256            APK 签名证书的 SHA-256 指纹
 * @param createdAt               创建时间
 */
public record AppReleaseDto(
        String id,
        String platform,
        Integer versionCode,
        String versionName,
        String downloadUrl,
        String downloadHost,
        String updateDescription,
        Boolean forceUpdate,
        Boolean published,
        String apkSha256,
        Long fileSizeBytes,
        Integer minSupportedVersionCode,
        String signerSha256,
        Instant createdAt
) {

    /**
     * R2-G-12: 从 Entity 转换为 DTO。
     */
    public static AppReleaseDto fromEntity(AppReleaseEntity entity) {
        return new AppReleaseDto(
                entity.getId(),
                entity.getPlatform(),
                entity.getVersionCode(),
                entity.getVersionName(),
                entity.getDownloadUrl(),
                entity.getDownloadHost(),
                entity.getUpdateDescription(),
                entity.getForceUpdate(),
                entity.getPublished(),
                entity.getApkSha256(),
                entity.getFileSizeBytes(),
                entity.getMinSupportedVersionCode(),
                entity.getSignerSha256(),
                entity.getCreatedAt()
        );
    }
}
