package com.yingshi.server.service.geocoding;

import com.yingshi.server.config.GeocodingProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-18 AC-7 / IA-5: Unit tests for AmapGeocodingService and NoopGeocodingService.
 * Uses JDK built-in HttpServer to mock the Amap regeo API — no external dependencies.
 */
class AmapGeocodingServiceTest {

    private HttpServer server;
    private AmapGeocodingService amapService;
    private NoopGeocodingService noopService;
    private final AtomicInteger requestCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.start();

        GeocodingProperties props = new GeocodingProperties(
                true,
                "test-key",
                "http://localhost:" + port,
                2000L
        );
        amapService = new AmapGeocodingService(props);
        noopService = new NoopGeocodingService();
        requestCount.set(0);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    // ---- TC-G01: NoopGeocodingService.reverseGeocode returns null (fallback) ----

    @Test
    void reverseGeocodeReturnsNullWhenDisabled() {
        // NoopGeocodingService always returns null regardless of input
        assertThat(noopService.reverseGeocode(39.9075, 116.39723)).isNull();
        assertThat(noopService.reverseGeocode(0.0, 0.0)).isNull();
        assertThat(noopService.reverseGeocode(-90.0, -180.0)).isNull();
    }

    // ---- TC-G02: AmapGeocodingService caches by 3-decimal key ----

    @Test
    void reverseGeocodeCachesBy3DecimalKey() {
        server.createContext("/", exchange -> {
            requestCount.incrementAndGet();
            String body = "{\"status\":\"1\",\"regeocode\":{\"formatted_address\":\"北京市天安门\"}}";
            exchange.sendResponseHeaders(200, body.getBytes().length);
            exchange.getResponseBody().write(body.getBytes());
            exchange.getResponseBody().close();
        });

        // First call — should hit server
        String result1 = amapService.reverseGeocode(39.907, 116.397);
        assertThat(result1).isEqualTo("北京市天安门");
        assertThat(requestCount.get()).isEqualTo(1);

        // Second call with identical coords — should hit cache (no new server request)
        String result2 = amapService.reverseGeocode(39.907, 116.397);
        assertThat(result2).isEqualTo("北京市天安门");
        assertThat(requestCount.get()).isEqualTo(1);

        // Third call with slightly different coords (within 3-decimal rounding) — cache hit
        // 39.9071 rounds to 39.907, 116.3971 rounds to 116.397
        String result3 = amapService.reverseGeocode(39.9071, 116.3971);
        assertThat(result3).isEqualTo("北京市天安门");
        assertThat(requestCount.get()).isEqualTo(1);
    }

    // ---- TC-G03: AmapGeocodingService handles HTTP 500 → returns null ----

    @Test
    void reverseGeocodeHandlesHttpFailure() {
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(500, 0);
            exchange.getResponseBody().close();
        });

        String result = amapService.reverseGeocode(39.907, 116.397);
        assertThat(result).isNull();
    }

    // ---- TC-G04: AmapGeocodingService handles timeout → returns null ----

    @Test
    void reverseGeocodeHandlesTimeout() {
        // Create service with very short timeout (100ms)
        GeocodingProperties shortTimeoutProps = new GeocodingProperties(
                true,
                "test-key",
                "http://localhost:" + server.getAddress().getPort(),
                100L
        );
        AmapGeocodingService shortTimeoutService = new AmapGeocodingService(shortTimeoutProps);

        server.createContext("/", exchange -> {
            try {
                Thread.sleep(2000); // Sleep longer than timeout
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });

        String result = shortTimeoutService.reverseGeocode(39.907, 116.397);
        assertThat(result).isNull();
    }
}
