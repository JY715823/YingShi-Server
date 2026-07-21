package com.yingshi.server.service.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yingshi.server.config.GeocodingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * FR-18: 高德地图反向地理编码实现。
 * - 用 JDK 21 HttpClient 调高德 regeo API
 * - Caffeine 缓存（3 位小数精度 ≈ 110m，1 万条，7 天 TTL）
 * - 任何异常返回 null，不阻断调用方
 * - 高德 location 参数顺序: 经度,纬度（lng,lat）
 */
@Service
@ConditionalOnProperty(prefix = "app.geocoding.amap", name = "enabled", havingValue = "true")
public class AmapGeocodingService implements GeocodingService {
    private static final Logger log = LoggerFactory.getLogger(AmapGeocodingService.class);

    private final GeocodingProperties props;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    // 缓存: 经纬度(精度3位小数=约110米) → 地址，最多 10000 条，7 天过期
    private final Cache<String, String> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofDays(7))
            .build();

    public AmapGeocodingService(GeocodingProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.timeoutMillis()))
                .build();
    }

    @Override
    public String reverseGeocode(double latitude, double longitude) {
        String cacheKey = String.format("%.3f,%.3f", latitude, longitude);
        String cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            // 高德 location 参数: 经度,纬度（lng 在前）
            String location = longitude + "," + latitude;
            String url = String.format("%s?key=%s&location=%s&output=json",
                    props.endpoint(),
                    URLEncoder.encode(props.key(), StandardCharsets.UTF_8),
                    URLEncoder.encode(location, StandardCharsets.UTF_8)
            );

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(props.timeoutMillis()))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Amap geocoding HTTP {} for lat={},lng={}", resp.statusCode(), latitude, longitude);
                return null;
            }

            JsonNode root = mapper.readTree(resp.body());
            String status = root.path("status").asText();
            if (!"1".equals(status)) {
                log.warn("Amap geocoding failed: infocode={}, info={}",
                        root.path("infocode").asText(), root.path("info").asText());
                return null;
            }

            String formatted = root.path("regeocode").path("formatted_address").asText();
            if (formatted == null || formatted.isEmpty()) {
                return null;
            }

            cache.put(cacheKey, formatted);
            return formatted;
        } catch (Exception e) {
            log.warn("Amap geocoding error for lat={},lng={}: {}", latitude, longitude, e.getMessage());
            return null;
        }
    }
}
