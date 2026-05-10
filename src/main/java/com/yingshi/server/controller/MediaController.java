package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.PageInfo;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.content.MediaDto;
import com.yingshi.server.dto.content.MediaFeedPage;
import com.yingshi.server.service.content.MediaService;
import com.yingshi.server.service.content.MediaFilePayload;
import com.yingshi.server.service.trash.TrashService;
import com.yingshi.server.dto.trash.TrashItemDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@AuthRequired
@Tag(name = "Media")
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;
    private final TrashService trashService;

    public MediaController(MediaService mediaService, TrashService trashService) {
        this.mediaService = mediaService;
        this.trashService = trashService;
    }

    @Operation(summary = "Get deduplicated media feed", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/feed")
    public ApiResponse<List<MediaDto>> getMediaFeed(
            @CurrentUser AuthenticatedUser currentUser,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer pageSize,
            HttpServletRequest request
    ) {
        if (cursor != null || pageSize != null) {
            MediaFeedPage page = mediaService.getMediaFeedPage(currentUser, cursor, pageSize);
            return ApiResponse.success(
                    requestId(request),
                    page.items(),
                    new PageInfo(1, page.pageSize(), page.nextCursor(), page.hasMore())
            );
        }
        return ApiResponse.success(requestId(request), mediaService.getMediaFeed(currentUser));
    }

    @Operation(summary = "Get local media file", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/files/{mediaId}")
    public ResponseEntity<?> getMediaFile(
            @PathVariable String mediaId,
            @RequestParam(defaultValue = "original") String variant,
            @CurrentUser AuthenticatedUser currentUser,
            @RequestHeader HttpHeaders requestHeaders
    ) {
        MediaFilePayload payload = mediaService.loadMediaFile(mediaId, variant, currentUser);
        List<HttpRange> ranges = requestHeaders.getRange();
        boolean servePartialContent = !ranges.isEmpty() &&
                payload.contentLength() != null &&
                payload.contentLength() > 0;
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity
                .status(servePartialContent ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=2592000, immutable")
                .contentType(MediaType.parseMediaType(payload.mimeType()))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION);
        if (!servePartialContent && payload.contentLength() != null && payload.contentLength() >= 0) {
            responseBuilder.contentLength(payload.contentLength());
        }
        if (payload.lastModifiedMillis() != null && payload.lastModifiedMillis() > 0L) {
            responseBuilder.lastModified(payload.lastModifiedMillis());
        }
        if (servePartialContent) {
            ByteRange byteRange = ByteRange.from(ranges.get(0), payload.contentLength());
            if (!byteRange.isSatisfiable()) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + payload.contentLength())
                        .build();
            }
            responseBuilder
                    .header(HttpHeaders.CONTENT_RANGE, byteRange.contentRangeHeader())
                    .contentLength(byteRange.length());
            try {
                return responseBuilder.body(new PartialResource(payload.resource(), byteRange));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to stream requested media byte range.", exception);
            }
        }
        return responseBuilder.body(payload.resource());
    }

    @Operation(summary = "System delete media", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{mediaId}")
    public ApiResponse<TrashItemDto> deleteMedia(
            @PathVariable String mediaId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), trashService.systemDeleteMedia(mediaId, currentUser));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }

    private record ByteRange(long start, long end, long totalLength) {
        static ByteRange from(HttpRange range, long totalLength) {
            long start = range.getRangeStart(totalLength);
            long end = range.getRangeEnd(totalLength);
            return new ByteRange(start, Math.min(end, totalLength - 1), totalLength);
        }

        boolean isSatisfiable() {
            return totalLength > 0 && start >= 0 && start < totalLength && end >= start;
        }

        long length() {
            return end - start + 1;
        }

        String contentRangeHeader() {
            return "bytes " + start + "-" + end + "/" + totalLength;
        }
    }

    private static final class PartialResource extends InputStreamResource {
        private final ByteRange byteRange;

        private PartialResource(Resource source, ByteRange byteRange) throws IOException {
            super(new BoundedRangeInputStream(source.getInputStream(), byteRange.start(), byteRange.length()));
            this.byteRange = byteRange;
        }

        @Override
        public long contentLength() {
            return byteRange.length();
        }
    }

    private static final class BoundedRangeInputStream extends FilterInputStream {
        private long remaining;

        private BoundedRangeInputStream(InputStream source, long offset, long length) throws IOException {
            super(source);
            skipFully(source, offset);
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = super.read();
            if (value != -1) {
                remaining -= 1;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int maxLength = (int) Math.min(length, remaining);
            int read = super.read(buffer, offset, maxLength);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        private static void skipFully(InputStream source, long offset) throws IOException {
            long remainingToSkip = offset;
            while (remainingToSkip > 0) {
                long skipped = source.skip(remainingToSkip);
                if (skipped <= 0) {
                    if (source.read() == -1) {
                        throw new IOException("Could not skip to requested media byte range.");
                    }
                    skipped = 1;
                }
                remainingToSkip -= skipped;
            }
        }
    }
}
