package com.yingshi.server.dto.apprelease;

/**
 * App 版本检查响应。
 *
 * <p>客户端通过比较 {@link #latestVersionCode()} 与本地 versionCode 判断是否需要更新。
 *
 * @param hasUpdate          是否有可用更新（latestVersionCode > 客户端上报的 versionCode）
 * @param latestVersionCode  服务端最新版本号
 * @param latestVersionName  服务端最新版本名
 * @param downloadUrl        APK 下载地址（相对路径会被客户端拼接 baseUrl）
 * @param updateDescription  更新说明（原样展示，可能含 \n）
 * @param forceUpdate        是否强制更新
 * @param sha256             APK 文件 SHA-256 校验值（用于下载完整性校验）
 * @param sizeBytes          APK 文件大小（字节）
 * @param minSupportedVersionCode 支持的最低客户端 versionCode
 * @param forceReason        强制更新原因（SECURITY/PROTOCOL/MIN_VERSION，暂为 null）
 */
public record AppReleaseCheckResponse(
        boolean hasUpdate,
        Integer latestVersionCode,
        String latestVersionName,
        String downloadUrl,
        String updateDescription,
        boolean forceUpdate,
        String sha256,
        Long sizeBytes,
        Integer minSupportedVersionCode,
        String forceReason
) {

    /**
     * 当服务端没有已发布版本时返回（如初始化阶段未插入数据）。
     */
    public static AppReleaseCheckResponse noUpdateAvailable() {
        return new AppReleaseCheckResponse(
                false,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null
        );
    }
}
