package com.yingshi.server.service.geocoding;

/**
 * FR-18: 反向地理编码服务接口。
 * 经纬度 -> 地址文字。失败时返回 null（不抛异常），保证调用方流程不阻断。
 */
public interface GeocodingService {
    /**
     * 反向地理编码: 经纬度 → 地址文字
     *
     * @param latitude  纬度
     * @param longitude 经度
     * @return 地址文字，失败或未启用时返回 null（不抛异常）
     */
    String reverseGeocode(double latitude, double longitude);
}
