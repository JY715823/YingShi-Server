package com.yingshi.server.config.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

/**
 * Global log masking converter: redacts latitude/longitude coordinates and
 * token/Bearer credentials from log messages before they are written to any
 * appender. Registered in logback-spring.xml via the conversion word "masked".
 *
 * Note: applies to pattern-based appenders that use %masked. For
 * LogstashEncoder (JSON output), source-level masking in services provides
 * the primary defense.
 */
public class MaskingConverter extends MessageConverter {

    private static final Pattern LATLNG =
            Pattern.compile("lat=(-?\\d+\\.?\\d*),lng=(-?\\d+\\.?\\d*)");
    /**
     * R2-F-9: 匹配坐标元组格式 {@code (lat,lng)}，整数部分限定 1-3 位以匹配经纬度范围
     * (纬度 -90~90, 经度 -180~180)，避免误匹配非坐标的数字对。
     * 示例: {@code (39.9042,116.4074)} → {@code (**,**)}
     */
    private static final Pattern COORD_TUPLE =
            Pattern.compile("\\((-?\\d{1,3}\\.?\\d*),\\s*(-?\\d{1,3}\\.?\\d*)\\)");
    private static final Pattern TOKEN =
            Pattern.compile("(token|Bearer)\\s*=\\s*\\S+");

    @Override
    public String convert(ILoggingEvent event) {
        String original = super.convert(event);
        if (original == null) {
            return null;
        }
        String masked = LATLNG.matcher(original).replaceAll("lat=***,lng=***");
        masked = COORD_TUPLE.matcher(masked).replaceAll("(**,**)");
        masked = TOKEN.matcher(masked).replaceAll("$1=***");
        return masked;
    }
}
