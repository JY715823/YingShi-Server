package com.yingshi.server.dto.life;

/**
 * Round 7: 更新媒体/大便事件位置的请求体。
 * latitude/longitude 必填，locationLabel 可选（为空时服务端调用 GeocodingService 逆地理编码回填）。
 */
public record UpdateLocationRequest(
        Double latitude,
        Double longitude,
        String locationLabel
) {
}
