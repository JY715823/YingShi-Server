package com.yingshi.server.service.upload;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.domain.UploadState;
import com.yingshi.server.domain.UploadTaskEntity;
import com.yingshi.server.dto.content.MediaDto;
import com.yingshi.server.dto.upload.UploadCompleteResponse;
import com.yingshi.server.dto.upload.UploadTokenRequest;
import com.yingshi.server.dto.upload.UploadTokenResponse;
import com.yingshi.server.mapper.ContentMapper;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.UploadTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class UploadService {

    private static final Duration UPLOAD_TTL = Duration.ofMinutes(30);

    private final UploadTaskRepository uploadTaskRepository;
    private final MediaRepository mediaRepository;
    private final ContentMapper contentMapper;
    private final LocalMediaStorageService localMediaStorageService;

    public UploadService(
            UploadTaskRepository uploadTaskRepository,
            MediaRepository mediaRepository,
            ContentMapper contentMapper,
            LocalMediaStorageService localMediaStorageService
    ) {
        this.uploadTaskRepository = uploadTaskRepository;
        this.mediaRepository = mediaRepository;
        this.contentMapper = contentMapper;
        this.localMediaStorageService = localMediaStorageService;
    }

    @Transactional
    public UploadTokenResponse createUploadToken(UploadTokenRequest request, AuthenticatedUser currentUser) {
        long nowMillis = Instant.now().toEpochMilli();
        Long capturedAtMillis = request.capturedAtMillis();
        long importedAtMillis = request.importedAtMillis() != null ? request.importedAtMillis() : nowMillis;
        long displayTimeMillis = request.displayTimeMillis() != null
                ? request.displayTimeMillis()
                : (capturedAtMillis != null ? capturedAtMillis : importedAtMillis);
        String displayTimeSource = normalizeDisplayTimeSource(
                request.displayTimeSource(),
                capturedAtMillis != null ? "ORIGINAL" : "IMPORTED"
        );

        UploadTaskEntity task = new UploadTaskEntity();
        task.setId(IdGenerator.newId("upload"));
        task.setLibraryId(currentUser.libraryId());
        task.setFileName(request.fileName().trim());
        task.setMediaType(parseMediaType(request.mediaType()));
        task.setMimeType(request.mimeType().trim());
        task.setFileSizeBytes(request.fileSizeBytes());
        task.setWidth(request.width());
        task.setHeight(request.height());
        task.setDurationMillis(request.durationMillis());
        task.setDisplayTimeMillis(displayTimeMillis);
        task.setCapturedAtMillis(capturedAtMillis);
        task.setImportedAtMillis(importedAtMillis);
        task.setDisplayTimeSource(displayTimeSource);
        task.setSourceFingerprint(normalizeSourceFingerprint(request.sourceFingerprint()));
        task.setState(UploadState.WAITING);
        task.setExpireAt(Instant.now().plus(UPLOAD_TTL));
        uploadTaskRepository.save(task);
        return new UploadTokenResponse(
                task.getId(),
                "local",
                "/api/uploads/" + task.getId() + "/file",
                task.getExpireAt().toEpochMilli(),
                task.getState().name().toLowerCase(Locale.ROOT)
        );
    }

    @Transactional
    public UploadCompleteResponse uploadFile(String uploadId, MultipartFile file, AuthenticatedUser currentUser) {
        UploadTaskEntity task = requireUploadTask(uploadId, currentUser.libraryId());
        if (task.getState() != UploadState.WAITING) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.UPLOAD_ALREADY_COMPLETED, "Upload task has already been completed.");
        }
        if (task.getExpireAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_FILE_MISMATCH, "Upload task has expired.");
        }
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_FILE_MISMATCH, "Uploaded file must not be empty.");
        }

        long actualFileSize = file.getSize();
        if (actualFileSize > 0 && actualFileSize != task.getFileSizeBytes()) {
            task.setFileSizeBytes(actualFileSize);
        }
        String actualContentType = normalizeMimeType(file.getContentType());
        if (actualContentType != null && !"application/octet-stream".equals(actualContentType)) {
            task.setMimeType(actualContentType);
        }

        MediaEntity duplicateMedia = findDuplicateMedia(task);
        if (duplicateMedia != null) {
            task.setState(UploadState.SUCCESS);
            task.setCompletedAt(Instant.now());
            task.setMediaId(duplicateMedia.getId());
            uploadTaskRepository.save(task);
            MediaDto mediaDto = contentMapper.toMediaDto(duplicateMedia, List.of());
            return new UploadCompleteResponse(task.getId(), "success", mediaDto);
        }

        String mediaId = IdGenerator.newId("media");
        LocalMediaStorageService.StoredFile storedFile = localMediaStorageService.storeOriginal(
                mediaId,
                task.getDisplayTimeMillis(),
                task.getMediaType(),
                task.getFileName(),
                file
        );

        MediaEntity media = buildMediaFromTask(mediaId, task, storedFile.storagePath());
        mediaRepository.save(media);

        task.setState(UploadState.SUCCESS);
        task.setCompletedAt(Instant.now());
        task.setStoredPath(storedFile.storagePath());
        task.setMediaId(media.getId());
        uploadTaskRepository.save(task);

        MediaDto mediaDto = contentMapper.toMediaDto(media, List.of());
        return new UploadCompleteResponse(task.getId(), "success", mediaDto);
    }

    private MediaEntity findDuplicateMedia(UploadTaskEntity task) {
        String sourceFingerprint = task.getSourceFingerprint();
        if (sourceFingerprint != null && !sourceFingerprint.isBlank()) {
            MediaEntity media = mediaRepository
                    .findFirstByLibraryIdAndSourceFingerprintAndDeletedAtIsNull(task.getLibraryId(), sourceFingerprint)
                    .orElse(null);
            if (media != null) {
                return media;
            }
        }
        if (task.getDurationMillis() == null) {
            return mediaRepository
                    .findFirstByLibraryIdAndMediaTypeAndMimeTypeAndSizeBytesAndDisplayTimeMillisAndWidthAndHeightAndDurationMillisIsNullAndDeletedAtIsNull(
                            task.getLibraryId(),
                            task.getMediaType(),
                            task.getMimeType(),
                            task.getFileSizeBytes(),
                            task.getDisplayTimeMillis(),
                            task.getWidth(),
                            task.getHeight()
                    )
                    .orElse(null);
        }
        return mediaRepository
                .findFirstByLibraryIdAndMediaTypeAndMimeTypeAndSizeBytesAndDisplayTimeMillisAndWidthAndHeightAndDurationMillisAndDeletedAtIsNull(
                        task.getLibraryId(),
                        task.getMediaType(),
                        task.getMimeType(),
                        task.getFileSizeBytes(),
                        task.getDisplayTimeMillis(),
                        task.getWidth(),
                        task.getHeight(),
                        task.getDurationMillis()
                )
                .orElse(null);
    }

    private UploadTaskEntity requireUploadTask(String uploadId, String libraryId) {
        return uploadTaskRepository.findByIdAndLibraryId(uploadId, libraryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.UPLOAD_NOT_FOUND, "Upload task was not found."));
    }

    private MediaEntity buildMediaFromTask(String mediaId, UploadTaskEntity task, String storedPath) {
        String mediaUrl = "/api/media/files/" + mediaId;

        MediaEntity media = new MediaEntity();
        media.setId(mediaId);
        media.setLibraryId(task.getLibraryId());
        media.setMediaType(task.getMediaType());
        media.setUrl(mediaUrl);
        media.setPreviewUrl(mediaUrl);
        media.setOriginalUrl(task.getMediaType() == MediaType.IMAGE ? mediaUrl : null);
        media.setVideoUrl(task.getMediaType() == MediaType.VIDEO ? mediaUrl : null);
        media.setCoverUrl(task.getMediaType() == MediaType.VIDEO ? mediaUrl : null);
        media.setMimeType(task.getMimeType());
        media.setSizeBytes(task.getFileSizeBytes());
        media.setWidth(task.getWidth());
        media.setHeight(task.getHeight());
        media.setAspectRatio(((double) task.getWidth()) / task.getHeight());
        media.setDurationMillis(task.getDurationMillis());
        media.setDisplayTimeMillis(task.getDisplayTimeMillis());
        media.setCapturedAtMillis(task.getCapturedAtMillis());
        media.setImportedAtMillis(task.getImportedAtMillis());
        media.setDisplayTimeSource(task.getDisplayTimeSource());
        media.setStoragePath(storedPath);
        media.setSourceFingerprint(task.getSourceFingerprint());
        return media;
    }

    private String normalizeSourceFingerprint(String rawFingerprint) {
        if (rawFingerprint == null || rawFingerprint.isBlank()) {
            return null;
        }
        return rawFingerprint.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeMimeType(String rawMimeType) {
        if (rawMimeType == null || rawMimeType.isBlank()) {
            return null;
        }
        return rawMimeType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayTimeSource(String rawSource, String fallback) {
        if (rawSource == null || rawSource.isBlank()) {
            return fallback;
        }
        String normalized = rawSource.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ORIGINAL", "IMPORTED", "MANUAL" -> normalized;
            default -> fallback;
        };
    }

    private MediaType parseMediaType(String mediaType) {
        try {
            return MediaType.valueOf(mediaType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "mediaType must be image or video.");
        }
    }
}
