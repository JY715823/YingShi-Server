package com.yingshi.server.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory token-bucket rate limiter scoped to auth endpoints.
 * Uses a lightweight custom implementation to avoid Bucket4j API volatility.
 * Each client IP gets an independent token bucket that refills greedily.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.auth.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilter(AuthRateLimitProperties properties) {
        FilterRegistrationBean<AuthRateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuthRateLimitFilter(properties));
        registration.addUrlPatterns(
                "/api/auth/login/challenge",
                "/api/auth/login/challenge/resend",
                "/api/auth/login/verify",
                "/api/auth/login/remembered",
                "/api/auth/refresh-token"
        );
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.setName("authRateLimitFilter");
        return registration;
    }

    static class AuthRateLimitFilter extends OncePerRequestFilter {

        private static final int MAX_BUCKETS = 10_000;
        private static final long BUCKET_TTL_NANOS = Duration.ofMinutes(10).toNanos();

        private final long capacity;
        private final long refillTokens;
        private final long refillDurationNanos;
        private final boolean trustForwardedHeaders;
        private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
        private final AtomicLong lastCleanupNanos = new AtomicLong(System.nanoTime());

        AuthRateLimitFilter(AuthRateLimitProperties properties) {
            this.capacity = properties.getMaxRequests();
            Duration window = properties.getWindow();
            this.refillTokens = capacity;
            this.refillDurationNanos = window.toNanos();
            this.trustForwardedHeaders = properties.isTrustForwardedHeaders();
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            // Periodic cleanup to prevent memory leak
            maybeCleanup();

            String clientIp = resolveClientIp(request);
            TokenBucket bucket = buckets.computeIfAbsent(clientIp, k -> new TokenBucket(capacity));
            // Touch the bucket to update last-access time
            bucket.lastAccessNanos.set(System.nanoTime());
            refill(bucket);

            if (bucket.tryConsume()) {
                long remaining = bucket.tokens.get();
                response.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
                response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, remaining)));
                filterChain.doFilter(request, response);
            } else {
                long retryAfterSeconds = Math.max(1, Duration.ofNanos(refillDurationNanos).toSeconds());
                response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
                response.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
                response.setHeader("X-RateLimit-Remaining", "0");
                response.sendError(429, "Too many requests. Please try again later.");
            }
        }

        /**
         * R3-DIST-001: Periodically remove stale buckets to prevent unbounded memory growth.
         * Runs at most once per minute; removes buckets not accessed in BUCKET_TTL_NANOS.
         */
        private void maybeCleanup() {
            long now = System.nanoTime();
            long lastCleanup = lastCleanupNanos.get();
            if (now - lastCleanup < Duration.ofMinutes(1).toNanos()) {
                return;
            }
            if (lastCleanupNanos.compareAndSet(lastCleanup, now)) {
                int before = buckets.size();
                buckets.entrySet().removeIf(entry ->
                        (now - entry.getValue().lastAccessNanos.get()) > BUCKET_TTL_NANOS
                );
                int removed = before - buckets.size();
                if (removed > 0) {
                    log.debug("RateLimit cleanup: removed {} stale buckets, {} remaining", removed, buckets.size());
                }
            }
        }

        /**
         * R3-DIST-001: Resolve client IP with defense against X-Forwarded-For spoofing.
         * Takes the RIGHTMOST IP from XFF (the one added by the trusted reverse proxy),
         * not the leftmost (which the client can spoof).
         * If no proxy headers present, falls back to remote address.
         */
        private String resolveClientIp(HttpServletRequest request) {
            if (trustForwardedHeaders) {
                String xff = request.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isBlank()) {
                    String[] parts = xff.split(",");
                    return parts[parts.length - 1].trim();
                }
                String xri = request.getHeader("X-Real-IP");
                if (xri != null && !xri.isBlank()) {
                    return xri.trim();
                }
            }
            return request.getRemoteAddr();
        }

        private void refill(TokenBucket bucket) {
            long now = System.nanoTime();
            long lastRefill = bucket.lastRefillNanos.get();
            if (now - lastRefill < refillDurationNanos) {
                return;
            }
            if (bucket.lastRefillNanos.compareAndSet(lastRefill, now)) {
                long elapsed = now - lastRefill;
                long tokensToAdd = (elapsed * refillTokens) / refillDurationNanos;
                if (tokensToAdd > 0) {
                    long current = bucket.tokens.get();
                    long newVal = Math.min(capacity, current + tokensToAdd);
                    bucket.tokens.set(newVal);
                }
            }
        }

        private static class TokenBucket {
            final AtomicLong tokens;
            final AtomicLong lastRefillNanos;
            final AtomicLong lastAccessNanos;

            TokenBucket(long initialTokens) {
                this.tokens = new AtomicLong(initialTokens);
                long now = System.nanoTime();
                this.lastRefillNanos = new AtomicLong(now);
                this.lastAccessNanos = new AtomicLong(now);
            }

            boolean tryConsume() {
                while (true) {
                    long current = tokens.get();
                    if (current <= 0) {
                        return false;
                    }
                    if (tokens.compareAndSet(current, current - 1)) {
                        return true;
                    }
                }
            }
        }
    }
}
