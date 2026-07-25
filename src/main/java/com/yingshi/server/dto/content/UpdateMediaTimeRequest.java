package com.yingshi.server.dto.content;

import jakarta.validation.constraints.NotNull;

/**
 * 修改媒体显示时间的请求体。
 * displayTimeMillis 必填，为新的显示时间（毫秒）。
 * 服务端会将 displayTimeSource 置为 "MANUAL"，标识用户手动修改。
 */
public record UpdateMediaTimeRequest(
        @NotNull Long displayTimeMillis
) {
}
