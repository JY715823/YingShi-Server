package com.yingshi.server.service;

import com.yingshi.server.domain.AppReleaseEntity;
import com.yingshi.server.dto.apprelease.AppReleaseCheckResponse;
import com.yingshi.server.repository.AppReleaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    private final AppReleaseRepository repository;

    public AppReleaseService(AppReleaseRepository repository) {
        this.repository = repository;
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
            platform = "android";
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
                Boolean.TRUE.equals(latest.getForceUpdate())
        );
    }
}
