package com.yingshi.server.service;

import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.AppReleaseEntity;
import com.yingshi.server.dto.apprelease.AppReleaseCheckResponse;
import com.yingshi.server.dto.apprelease.AppReleaseDto;
import com.yingshi.server.repository.AppReleaseRepository;
import com.yingshi.server.service.storage.ObjectMetadata;
import com.yingshi.server.service.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * App 版本发布与检查服务。
 *
 * <p>核心场景：客户端启动时调用 {@link #checkForUpdate(String, Integer)}，
 * 服务端根据客户端上报的 versionCode 与最新已发布版本比较，返回是否需要更新。
 *
 * <p>不发版也能运行：数据库为空时返回 {@link AppReleaseCheckResponse#noUpdateAvailable()}，
 * 客户端不会弹窗，App 正常使用。
 */
@Service
public class AppReleaseService {

    private static final Logger log = LoggerFactory.getLogger(AppReleaseService.class);

    private static final String PLATFORM_ANDROID = "android";
    private static final String APK_CONTENT_TYPE = "application/vnd.android.package-archive";

    private final AppReleaseRepository repository;
    // R2-G-12: 注入对象存储服务，用于存储上传的 APK 文件
    private final ObjectStorageService objectStorageService;

    public AppReleaseService(AppReleaseRepository repository, ObjectStorageService objectStorageService) {
        this.repository = repository;
        this.objectStorageService = objectStorageService;
    }

    /**
     * 检查指定平台是否有可用更新。
     *
     * @param platform       客户端平台标识，当前仅 "android"
     * @param clientVersionCode 客户端当前 versionCode（来自 BuildConfig）
     * @return 检查结果，hasUpdate=false 表示无需更新
     */
    public AppReleaseCheckResponse checkForUpdate(String platform, Integer clientVersionCode) {
        if (platform == null || platform.isBlank()) {
            platform = PLATFORM_ANDROID;
        }
        AppReleaseEntity latest = repository.findLatestPublished(platform).orElse(null);
        if (latest == null) {
            log.debug("checkForUpdate: no published release for platform={}, returning noUpdate", platform);
            return AppReleaseCheckResponse.noUpdateAvailable();
        }
        Integer latestCode = latest.getVersionCode();
        boolean hasUpdate = clientVersionCode == null || latestCode > clientVersionCode;
        log.debug("checkForUpdate: platform={}, client={}, latest={}, hasUpdate={}",
                platform, clientVersionCode, latestCode, hasUpdate);
        return new AppReleaseCheckResponse(
                hasUpdate,
                latestCode,
                latest.getVersionName(),
                latest.getDownloadUrl(),
                latest.getUpdateDescription(),
                Boolean.TRUE.equals(latest.getForceUpdate()),
                latest.getApkSha256(),
                latest.getFileSizeBytes(),
                latest.getMinSupportedVersionCode(),
                null
        );
    }

    /**
     * R2-G-12: 发布 APK 版本（事务性）。
     * 流程：
     * 1. 校验 APK 文件（非空、内容类型）
     * 2. 上传到对象存储（计算 SHA-256、获取文件大小）
     * 3. 写入 app_releases 表（事务内）
     * 4. 返回发布记录 DTO
     *
     * 注意：APK 签名校验（signer_sha256）当前为保守实现，仅记录 SHA-256，
     * 不实际调用 jarsigner/apksigner 校验签名。后续可扩展。
     *
     * @param apkFile                  上传的 APK 文件
     * @param versionName              用户可见版本名（如 "1.0"、"1.1.0"）
     * @param versionCode              整数版本号
     * @param minSupportedVersionCode 支持的最低客户端 versionCode（可空）
     * @param forceUpdate              是否强制更新
     * @return 发布记录 DTO
     */
    @Transactional
    public AppReleaseDto publishRelease(
            MultipartFile apkFile,
            String versionName,
            Integer versionCode,
            Integer minSupportedVersionCode,
            boolean forceUpdate
    ) {
        // 1. 校验输入
        if (apkFile == null || apkFile.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "APK file is required.");
        }
        if (versionName == null || versionName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "versionName is required.");
        }
        if (versionCode == null || versionCode <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "versionCode must be a positive integer.");
        }
        // 校验文件类型（宽容：允许 application/octet-stream 或空 content type）
        String contentType = apkFile.getContentType();
        String originalFilename = apkFile.getOriginalFilename();
        boolean isApkByName = originalFilename != null && originalFilename.toLowerCase().endsWith(".apk");
        boolean isApkByContentType = APK_CONTENT_TYPE.equalsIgnoreCase(contentType)
                || contentType == null
                || "application/octet-stream".equalsIgnoreCase(contentType);
        if (!isApkByName && !isApkByContentType) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_CONTENT_TYPE_MISMATCH,
                    "Uploaded file is not an APK.");
        }

        // 2. 上传到对象存储（计算 SHA-256、获取文件大小）
        String objectKey = buildApkObjectKey(versionName, versionCode);
        ObjectMetadata metadata;
        try (InputStream inputStream = apkFile.getInputStream()) {
            metadata = objectStorageService.put(
                    objectKey,
                    APK_CONTENT_TYPE,
                    apkFile.getSize(),
                    inputStream
            );
        } catch (IOException exception) {
            log.error("publishRelease: failed to read APK stream. versionCode={}", versionCode, exception);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR,
                    "Failed to read uploaded APK file.");
        }
        String apkSha256 = metadata.checksum();
        Long fileSizeBytes = metadata.sizeBytes();
        log.info("publishRelease: APK stored. objectKey={} sha256={} size={}",
                objectKey, apkSha256, fileSizeBytes);

        // 3. 写入 app_releases 表（事务内）
        AppReleaseEntity entity = new AppReleaseEntity();
        entity.setPlatform(PLATFORM_ANDROID);
        entity.setVersionCode(versionCode);
        entity.setVersionName(versionName);
        // downloadUrl 为相对路径，客户端拼接 downloadHost 使用
        entity.setDownloadUrl("/" + objectKey);
        entity.setForceUpdate(forceUpdate);
        entity.setPublished(true);
        entity.setApkSha256(apkSha256);
        entity.setFileSizeBytes(fileSizeBytes);
        entity.setMinSupportedVersionCode(minSupportedVersionCode);
        // TODO R2-G-12: 后续扩展实际调用 jarsigner/apksigner 校验签名并填充 signerSha256
        // 当前保守实现：signerSha256 留空，客户端通过 apkSha256 校验下载完整性即可
        AppReleaseEntity saved = repository.save(entity);
        log.info("publishRelease: release published. id={} versionCode={} versionName={}",
                saved.getId(), saved.getVersionCode(), saved.getVersionName());

        return AppReleaseDto.fromEntity(saved);
    }

    /**
     * 构建 APK 在对象存储中的 key。
     * 格式：apk/yingshi-{versionName}-{versionCode}.apk
     */
    private String buildApkObjectKey(String versionName, Integer versionCode) {
        String safeVersionName = versionName.replaceAll("[^a-zA-Z0-9.]", "-");
        return String.format("apk/yingshi-%s-%d.apk", safeVersionName, versionCode);
    }
}

