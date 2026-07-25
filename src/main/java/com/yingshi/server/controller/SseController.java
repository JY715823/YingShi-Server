package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.service.sse.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE (Server-Sent Events) 端点。
 *
 * 客户端通过 GET /api/sse/subscribe 建立长连接，接收实时推送事件。
 * 替代 FCM（在中国大陆被 GFW 封锁）和轮询（延迟高、App 被杀后失效）。
 *
 * 鉴权：Bearer JWT token（与其它 @AuthRequired 端点一致）
 * 超时：60 分钟（超时后客户端自动重连）
 * 心跳：服务端每 30 秒发送 :heartbeat 注释行
 * R3-DIST-003: 支持 Last-Event-ID 断线补偿
 */
@AuthRequired
@Tag(name = "SSE")
@RestController
@RequestMapping("/api/sse")
public class SseController {
    private static final Logger log = LoggerFactory.getLogger(SseController.class);
    private static final long SSE_TIMEOUT_MILLIS = 60L * 60L * 1000L; // 1 hour

    private final SseEmitterRegistry sseEmitterRegistry;

    public SseController(SseEmitterRegistry sseEmitterRegistry) {
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @Operation(summary = "Subscribe to server-sent events for real-time push", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/subscribe")
    public SseEmitter subscribe(
            @CurrentUser AuthenticatedUser currentUser,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
    ) {
        log.info("SSE subscribe request: libraryId={}, userId={}, lastEventId={}",
                currentUser.libraryId(), currentUser.userId(), lastEventId);
        SseEmitter emitter = sseEmitterRegistry.register(
            currentUser.libraryId(),
            currentUser.userId(),
            SSE_TIMEOUT_MILLIS
        );
        // 发送 hello 事件，让客户端确认连接已建立
        try {
            emitter.send(SseEmitter.event().name("hello").data("connected"));
        } catch (Exception ignored) {
            // 客户端会在重连时重新建立连接
        }

        // R3-DIST-003: Replay missed events if client provides Last-Event-ID
        if (lastEventId != null && !lastEventId.isBlank()) {
            try {
                long lastId = Long.parseLong(lastEventId);
                sseEmitterRegistry.replayEvents(currentUser.libraryId(), lastId, emitter);
            } catch (NumberFormatException e) {
                log.warn("SSE subscribe: invalid Last-Event-ID header: {}", lastEventId);
            }
        }

        return emitter;
    }
}
