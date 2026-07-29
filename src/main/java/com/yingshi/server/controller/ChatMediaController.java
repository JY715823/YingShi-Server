package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.service.chat.ChatMediaService;
import com.yingshi.server.service.storage.ObjectMetadata;
import com.yingshi.server.service.storage.StoredObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@AuthRequired
@Tag(name = "Chat Media")
@RestController
@RequestMapping("/api/chat/imported/media")
public class ChatMediaController {

    private static final String BASE_PATH = "/api/chat/imported/media/";

    private final ChatMediaService chatMediaService;

    public ChatMediaController(ChatMediaService chatMediaService) {
        this.chatMediaService = chatMediaService;
    }

    @Operation(summary = "Upload a chat imported media file", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/upload")
    public ApiResponse<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("chatStableKey") String chatStableKey,
            @RequestParam(value = "md5", required = false) String md5,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            fileName = "unnamed";
        }
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        String objectKey = chatMediaService.upload(
                currentUser.libraryId(),
                chatStableKey,
                fileName,
                contentType,
                md5,
                file.getInputStream(),
                file.getSize()
        );
        Map<String, String> result = new HashMap<>();
        result.put("objectKey", objectKey);
        return ApiResponse.success(requestId(request), result);
    }

    @Operation(summary = "Download a chat imported media file", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/**")
    public ResponseEntity<Resource> download(
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        String objectKey = extractObjectKey(request);
        if (objectKey == null || objectKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        // R3-AUTH-001 / R1-D-1: Verify objectKey belongs to current user's library.
        // Upload key format is "chat-imports/{libraryId}/{chatStableKey}/resources/{fileName}"
        // (see ChatMediaService#upload), so the prefix check must include the
        // "chat-imports/" segment — otherwise every HEAD/download returns 403.
        if (!objectKey.startsWith("chat-imports/" + currentUser.libraryId() + "/")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        StoredObject storedObject = chatMediaService.download(objectKey);
        if (storedObject == null || storedObject.resource() == null) {
            return ResponseEntity.notFound().build();
        }
        ObjectMetadata metadata = storedObject.metadata();
        String mimeType = metadata != null && metadata.contentType() != null
                ? metadata.contentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String fileName = extractFileName(objectKey);
        String contentDisposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString();
        // R1-D-4: Advertise Range support and emit standard metadata headers so
        // clients can resume downloads and verify checksums.
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
        headers.setContentType(MediaType.parseMediaType(mimeType));
        if (metadata != null) {
            if (metadata.sizeBytes() != null) {
                headers.setContentLength(metadata.sizeBytes());
            }
            if (metadata.checksum() != null) {
                headers.setETag('"' + metadata.checksum() + '"');
                headers.set("X-MD5", metadata.checksum());
            }
        }
        HttpStatus status = (rangeHeader != null && rangeHeader.startsWith("bytes="))
                ? HttpStatus.PARTIAL_CONTENT
                : HttpStatus.OK;
        return ResponseEntity.status(status).headers(headers).body(storedObject.resource());
    }

    @Operation(summary = "Check if a chat imported media file exists", security = @SecurityRequirement(name = "bearerAuth"))
    @RequestMapping(value = "/**", method = RequestMethod.HEAD)
    public ResponseEntity<Void> exists(
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        String objectKey = extractObjectKey(request);
        if (objectKey == null || objectKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        // R3-AUTH-001 / R1-D-1: Same prefix check as download (see comment above).
        if (!objectKey.startsWith("chat-imports/" + currentUser.libraryId() + "/")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<ObjectMetadata> metadata = chatMediaService.exists(objectKey);
        if (metadata.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // R1-D-5: Return Content-Length and ETag so clients can validate cache and
        // conditionally issue Range requests without a full GET.
        HttpHeaders headers = new HttpHeaders();
        ObjectMetadata m = metadata.get();
        if (m.sizeBytes() != null) {
            headers.setContentLength(m.sizeBytes());
        }
        if (m.checksum() != null) {
            headers.setETag('"' + m.checksum() + '"');
            headers.set("X-MD5", m.checksum());
        }
        return ResponseEntity.ok().headers(headers).build();
    }

    private String extractObjectKey(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int idx = uri.indexOf(BASE_PATH);
        if (idx < 0) {
            return null;
        }
        String raw = uri.substring(idx + BASE_PATH.length());
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    private String extractFileName(String objectKey) {
        int lastSlash = objectKey.lastIndexOf('/');
        return lastSlash >= 0 ? objectKey.substring(lastSlash + 1) : objectKey;
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }
}
