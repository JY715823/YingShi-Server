package com.yingshi.server.service.geocoding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * FR-18: 反向地理编码默认空实现。
 * 当 app.geocoding.amap.enabled=false 或缺失该属性时注入，
 * 静默返回 null，不阻断调用方流程。
 *
 * 注: 使用 @ConditionalOnProperty 反向匹配代替 @ConditionalOnMissingBean。
 * Spring Boot 文档明确指出 @ConditionalOnMissingBean 仅推荐用于 auto-configuration
 * 类的 @Bean 方法, 在 component-scanned 的 @Service 类上不可靠 (评估顺序问题)。
 * 这里用 havingValue="false" matchIfMissing=true 与 AmapGeocodingService 形成互斥,
 * 是 Spring Boot 官方推荐的 fallback bean 模式。
 */
@Service
@ConditionalOnProperty(prefix = "app.geocoding.amap", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopGeocodingService implements GeocodingService {
    @Override
    public String reverseGeocode(double latitude, double longitude) {
        return null;
    }
}
