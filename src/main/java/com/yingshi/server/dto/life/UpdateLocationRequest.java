package com.yingshi.server.dto.life;

/**
 * Round 7: 更新媒体/大便事件位置的请求体。
 * latitude/longitude 必填，locationLabel 可选（为空时服务端调用 GeocodingService 逆地理编码回填）。
 *
 * R2-F-5: 新增 4 个可选坐标元数据字段（capturedAt/accuracy/provider/coordSystem），
 * 用于服务端日志诊断与未来持久化扩展。所有新字段均可选，旧客户端不传时为 null，保持向后兼容。
 * 本轮不强制持久化到 entity（如需持久化需新增迁移）。
 */
public record UpdateLocationRequest(
        Double latitude,
        Double longitude,
        String locationLabel,
        Long capturedAt,
        Double accuracy,
        String provider,
        String coordSystem,
        // V52: 位置来源 exif=实时定位/媒体自带, inferred=轨迹点推断, manual=用户手动修改。
        // 旧客户端不传时为 null，服务端按 manual 处理。
        String locationSource
) {
    /**
     * 向后兼容构造器：仅传核心三字段，元数据字段默认 null。
     * 旧调用方（含既有测试）无需修改即可继续编译。
     */
    public UpdateLocationRequest(Double latitude, Double longitude, String locationLabel) {
        this(latitude, longitude, locationLabel, null, null, null, null, null);
    }
}
