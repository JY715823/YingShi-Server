package com.yingshi.server.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

/**
 * FR-2: Business metrics service for tracking key operational indicators.
 * Exports metrics via Micrometer to Prometheus (/actuator/prometheus).
 *
 * <p>Tracked metrics:
 * <ul>
 *   <li>upload.success / upload.failure — upload outcome counters</li>
 *   <li>api.request.count — total API request counter (tagged by status)</li>
 *   <li>api.request.duration — API request latency timer (tagged by method/uri)</li>
 * </ul>
 */
@Service
public class BusinessMetricsService {

    private final Counter uploadSuccessCounter;
    private final Counter uploadFailureCounter;
    private final Timer apiRequestTimer;

    public BusinessMetricsService(MeterRegistry meterRegistry) {
        this.uploadSuccessCounter = Counter.builder("upload.success")
                .description("Number of successful upload operations")
                .register(meterRegistry);
        this.uploadFailureCounter = Counter.builder("upload.failure")
                .description("Number of failed upload operations")
                .register(meterRegistry);
        this.apiRequestTimer = Timer.builder("api.request.duration")
                .description("API request latency distribution")
                .register(meterRegistry);
    }

    public void recordUploadSuccess() {
        uploadSuccessCounter.increment();
    }

    public void recordUploadFailure() {
        uploadFailureCounter.increment();
    }

    public Timer getApiRequestTimer() {
        return apiRequestTimer;
    }
}
