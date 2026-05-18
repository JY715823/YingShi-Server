package com.yingshi.server.service.content;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Optional;

public record MediaFilePayload(
        ResourceLoader resourceLoader,
        RangeResourceLoader rangeResourceLoader,
        boolean rangeLoaderRequiresBounding,
        String mimeType,
        Long contentLength,
        Long lastModifiedMillis
) {

    public Resource resource() {
        return resourceLoader.load();
    }

    public Optional<Resource> loadRange(long start, long endInclusive) throws IOException {
        if (rangeResourceLoader == null) {
            return Optional.empty();
        }
        return Optional.of(rangeResourceLoader.loadRange(start, endInclusive));
    }

    @FunctionalInterface
    public interface ResourceLoader {
        Resource load();
    }

    @FunctionalInterface
    public interface RangeResourceLoader {
        Resource loadRange(long start, long endInclusive) throws IOException;
    }
}
