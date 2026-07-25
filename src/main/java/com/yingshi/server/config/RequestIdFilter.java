package com.yingshi.server.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String RESPONSE_TIME_HEADER = "X-Response-Time";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_RESPONSE_TIME = "responseTime";
    public static final String MDC_HTTP_METHOD = "httpMethod";
    public static final String MDC_HTTP_URL = "httpUrl";

    /** Requests slower than this threshold (ms) get a WARN log entry. */
    private static final long SLOW_REQUEST_THRESHOLD_MS = 1000;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        // FR-1: Add request timing and HTTP context to MDC
        long startTime = System.nanoTime();
        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_HTTP_METHOD, request.getMethod());
        MDC.put(MDC_HTTP_URL, request.getRequestURI());

        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedNanos = System.nanoTime() - startTime;
            long elapsedMs = elapsedNanos / 1_000_000;
            String responseTimeStr = elapsedMs + "ms";
            response.setHeader(RESPONSE_TIME_HEADER, responseTimeStr);
            MDC.put(MDC_RESPONSE_TIME, responseTimeStr);

            // Log slow requests at WARN level
            if (elapsedMs > SLOW_REQUEST_THRESHOLD_MS) {
                log.warn("Slow request: {} {} took {}ms (threshold: {}ms)",
                        request.getMethod(), request.getRequestURI(), elapsedMs, SLOW_REQUEST_THRESHOLD_MS);
            }

            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_RESPONSE_TIME);
            MDC.remove(MDC_HTTP_METHOD);
            MDC.remove(MDC_HTTP_URL);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestIdFromHeader = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(requestIdFromHeader)) {
            // Sanitize: limit length to prevent header injection
            return requestIdFromHeader.length() > 128
                    ? requestIdFromHeader.substring(0, 128)
                    : requestIdFromHeader;
        }
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }
}
