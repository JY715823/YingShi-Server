package com.yingshi.server.dto.health;

/**
 * 公开健康检查响应（最小化，不暴露内部信息）。
 *
 * <p>用于未认证的 /api/health 端点，仅返回 status 字段，
 * 不包含 profile、version、依赖检查结果等敏感信息。
 *
 * @param status 服务状态（"UP" 或 "DEGRADED"）
 */
public record PublicHealthResponse(
        String status
) {
}
