package com.yingshi.server.dto.life;

/**
 * FR-18: 添加排便事件请求体。
 * 所有字段可选，向后兼容（旧客户端不发 body 时 request 为 null）。
 */
public record AddBowelEventRequest(
        Double latitude,
        Double longitude,
        String locationLabel
) {
    public AddBowelEventRequest {
        // 默认构造，允许全 null
    }
}
