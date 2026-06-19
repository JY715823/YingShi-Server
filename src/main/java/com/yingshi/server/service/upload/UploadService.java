package com.yingshi.server.service.upload;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.config.StorageProperties;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.domain.UploadState;
import com.yingshi.server.domain.UploadTaskEntity;
import com.yingshi.server.dto.content.MediaDto;
import com.yingshi.server.dto.upload.UploadCompleteResponse;
import com.yingshi.server.dto.upload.UploadConfirmRequest;
import com.yingshi.server.dto.upload.UploadTaskResponse;
import com.yingshi.server.dto.upload.UploadTokenRequest;
import com.yingshi.server.dto.upload.UploadTokenResponse;
import com.yingshi.server.mapper.ContentMapper;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.UploadTaskRepository;
import com.yingshi.server.service.storage.ObjectKeyPolicy;
import com.yingshi.server.service.storage.ObjectMetadata;
import com.yingshi.server.service.storage.ObjectStorageService;
import com.yingshi.server.service.storage.PresignedObjectUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class UploadService {

    private static final Duration UPLOAD_TTL = Duration.ofMinutes(30);
    private static final Duration HISTORY_RETENTION = Duration.ofDays(30);
    private static final int PREVIEW_MAX_DIMENSION = 1280;
    private static final int DEFAULT_HISTORY_PAGE_SIZE = 50;
    private static final int MAX_HISTORY_PAGE_SIZE = 200;
    private static final Logger logger = LoggerFactory.getLogger(UploadService.class);

    private final UploadTaskRepository uploadTaskRepository;
    private final MediaRepository mediaRepository;
    private final ContentMapper contentMapper;
    private final LocalMediaStorageService localMediaStorageService;
    private final ObjectStorageService objectStorageService;
    private final StorageProperties storageProperties;

    public UploadService(
            UploadTaskRepository uploadTaskRepository,
            MediaRepository mediaRepository,
            ContentMapper contentMapper,
            LocalMediaStorageService localMediaStorageService,
            ObjectStorageService objectStorageService,
            StorageProperties storageProperties
    ) {
        this.uploadTaskRepository = uploadTaskRepository;
        this.mediaRepository = mediaRepository;
        this.contentMapper = contentMapper;
        this.localMediaStorageService = localMediaStorageService;
        this.objectStorageService = objectStorageService;
        this.storageProperties = storageProperties;
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

        MediaType mediaType = parseMediaType(request.mediaType());
        String mediaId = IdGenerator.newId("media");
        String objectKey = localMediaStorageService.originalObjectKeyForUpload(
                mediaId,
                displayTimeMillis,
                mediaType,
                request.fileName()
        );

        UploadTaskEntity task = new UploadTaskEntity();
        task.setId(IdGenerator.newId("upload"));
        task.setLibraryId(currentUser.libraryId());
        task.setFileName(request.fileName().trim());
        task.setMediaType(mediaType);
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
        task.setUploadedByUserId(currentUser.userId());
        task.setOperationId(normalizeBounded(request.operationId(), 255));
        task.setOperationType(normalizeOperationType(request.operationType()));
        task.setOperationTitle(normalizeBounded(request.operationTitle(), 255));
        task.setOperationMediaCount(request.operationMediaCount());
        task.setSourceItemId(normalizeBounded(request.sourceItemId(), 255));
        task.setState(UploadState.WAITING);
        task.setExpireAt(Instant.now().plus(UPLOAD_TTL));
        task.setMediaId(mediaId);
        boolean directUpload = storageProperties.directUploadEnabled()
                && objectStorageService.supportsPresignedPut()
                && objectStorageService.isDirectUploadAvailable();
        if (directUpload) {
            task.setStoredPath(objectKey);
        }
        uploadTaskRepository.save(task);

        if (directUpload) {
            PresignedObjectUrl presignedUrl = objectStorageService.presignPut(
                    objectKey,
                    task.getMimeType(),
                    task.getFileSizeBytes(),
                    storageProperties.signedUrlTtl()
            ).orElseThrow(() -> new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.UPLOAD_STORAGE_ERROR,
                    "Storage provider does not support direct upload."
            ));
            return new UploadTokenResponse(
                    task.getId(),
                    localMediaStorageService.provider(),
                    presignedUrl.url(),
                    presignedUrl.expiresAtMillis(),
                    task.getState().name().toLowerCase(Locale.ROOT),
                    "presigned-put",
                    objectKey,
                    presignedUrl.headers(),
                    "/api/uploads/" + task.getId() + "/confirm"
            );
        }

        return new UploadTokenResponse(
                task.getId(),
                localMediaStorageService.provider(),
                "/api/uploads/" + task.getId() + "/file",
                task.getExpireAt().toEpochMilli(),
                task.getState().name().toLowerCase(Locale.ROOT),
                "multipart",
                null,
                Map.of(),
                "/api/uploads/" + task.getId() + "/confirm"
        );
    }

    @Transactional(readOnly = true)
    public List<UploadTaskResponse> listUploadHistory(
            AuthenticatedUser currentUser,
            String rawState,
            String rawOperationType,
            Integer requestedPageSize
    ) {
        UploadState state = parseOptionalState(rawState);
        String operationType = normalizeOperationType(rawOperationType);
        int pageSize = requestedPageSize == null
                ? DEFAULT_HISTORY_PAGE_SIZE
                : Math.max(1, Math.min(requestedPageSize, MAX_HISTORY_PAGE_SIZE));
        Instant updatedAfter = Instant.now().minus(HISTORY_RETENTION);
        return uploadTaskRepository.findVisibleHistory(
                        currentUser.libraryId(),
                        currentUser.userId(),
                        updatedAfter,
                        state,
                        operationType
                )
                .stream()
                .limit(pageSize)
                .map(this::toUploadTaskResponse)
                .toList();
    }

    @Transactional
    public UploadCompleteResponse uploadFile(String uploadId, MultipartFile file, AuthenticatedUser currentUser) {
        UploadTaskEntity task = requireUploadTask(uploadId, currentUser.libraryId());
        if (task.getState() != UploadState.WAITING) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.UPLOAD_ALREADY_COMPLETED, "Upload task has already been completed.");
        }
        if (task.getExpireAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_EXPIRED, "Upload task has expired.");
        }
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_OBJECT_INVALID, "Uploaded file must not be empty.");
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
            task.setStoredPath(duplicateMedia.getStoragePath());
            uploadTaskRepository.save(task);
            MediaDto mediaDto = contentMapper.toMediaDto(duplicateMedia, List.of());
            return new UploadCompleteResponse(task.getId(), "success", mediaDto);
        }

        String mediaId = normalizeNullable(task.getMediaId());
        if (mediaId == null) {
            mediaId = IdGenerator.newId("media");
        }
        LocalMediaStorageService.StoredFile storedFile = localMediaStorageService.storeOriginal(
                mediaId,
                task.getDisplayTimeMillis(),
                task.getMediaType(),
                task.getFileName(),
                file
        );

        MediaEntity media = null;
        try {
            media = buildMediaFromTask(mediaId, task, storedFile);
            warmPreviewIfPossible(media);
            mediaRepository.save(media);
        } catch (Exception exception) {
            deleteTaskStorageObject(storedFile.storagePath());
            if (media != null) {
                deleteTaskStorageObject(media.getPreviewObjectKey());
                deleteTaskStorageObject(media.getCoverObjectKey());
            }
            throw exception;
        }

        task.setState(UploadState.SUCCESS);
        task.setCompletedAt(Instant.now());
        task.setStoredPath(storedFile.storagePath());
        task.setMediaId(media.getId());
        uploadTaskRepository.save(task);

        MediaDto mediaDto = contentMapper.toMediaDto(media, List.of());
        return new UploadCompleteResponse(task.getId(), "success", mediaDto);
    }

    @Transactional(readOnly = true)
    public UploadTaskResponse getUploadTask(String uploadId, AuthenticatedUser currentUser) {
        UploadTaskEntity task = requireUploadTask(uploadId, currentUser.libraryId());
        return toUploadTaskResponse(task);
    }

    @Transactional
    public UploadTaskResponse confirmUpload(
            String uploadId,
            UploadConfirmRequest request,
            AuthenticatedUser currentUser
    ) {
        UploadTaskEntity task = requireUploadTask(uploadId, currentUser.libraryId());
        if (task.getState() == UploadState.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.UPLOAD_ALREADY_COMPLETED, "Upload task has already been cancelled.");
        }
        if (task.getState() == UploadState.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.UPLOAD_ALREADY_COMPLETED, "Upload task has already failed.");
        }
        if (task.getState() == UploadState.SUCCESS) {
            return toUploadTaskResponse(task);
        }
        if (task.getExpireAt().isBefore(Instant.now()) && task.getState() == UploadState.WAITING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_EXPIRED, "Upload task has expired.");
        }
        if (task.getState() == UploadState.WAITING) {
            String objectKey = normalizeConfirmObjectKey(task, request);
            ObjectMetadata metadata = localMediaStorageService.metadataForObjectKey(objectKey);
            if (metadata == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_OBJECT_INVALID, "Uploaded object was not found.");
            }
            validateUploadedObject(task, metadata);

            MediaEntity duplicateMedia = findDuplicateMedia(task);
            if (duplicateMedia != null) {
                deleteTaskStorageObject(objectKey);
                task.setState(UploadState.SUCCESS);
                task.setCompletedAt(Instant.now());
                task.setMediaId(duplicateMedia.getId());
                task.setStoredPath(duplicateMedia.getStoragePath());
                uploadTaskRepository.save(task);
                return toUploadTaskResponse(task);
            }

            String mediaId = normalizeNullable(task.getMediaId());
            if (mediaId == null) {
                mediaId = IdGenerator.newId("media");
            }
            LocalMediaStorageService.StoredFile storedFile = storedFileFromMetadata(objectKey, metadata);
            MediaEntity media = null;
            try {
                media = buildMediaFromTask(mediaId, task, storedFile);
                warmPreviewIfPossible(media);
                mediaRepository.save(media);
            } catch (Exception exception) {
                deleteTaskStorageObject(objectKey);
                if (media != null) {
                    deleteTaskStorageObject(media.getPreviewObjectKey());
                    deleteTaskStorageObject(media.getCoverObjectKey());
                }
                throw exception;
            }

            task.setState(UploadState.SUCCESS);
            task.setCompletedAt(Instant.now());
            task.setStoredPath(storedFile.storagePath());
            task.setMediaId(media.getId());
            uploadTaskRepository.save(task);
        }
        return toUploadTaskResponse(task);
    }

    @Transactional
    public UploadTaskResponse cancelUpload(String uploadId, AuthenticatedUser currentUser) {
        UploadTaskEntity task = requireUploadTask(uploadId, currentUser.libraryId());
        if (task.getState() == UploadState.SUCCESS) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.UPLOAD_ALREADY_COMPLETED, "Completed upload task cannot be cancelled.");
        }
        if (task.getState() != UploadState.CANCELLED) {
            task.setState(UploadState.CANCELLED);
            task.setErrorMessage("Upload task was cancelled.");
            task.setCompletedAt(Instant.now());
            uploadTaskRepository.save(task);
            deleteTaskStorageObject(task.getStoredPath());
        }
        return toUploadTaskResponse(task);
    }

    @Transactional
    public UploadTaskResponse dismissUpload(String uploadId, AuthenticatedUser currentUser) {
        UploadTaskEntity task = requireUploadTask(uploadId, currentUser.libraryId());
        ensureTaskUploader(task, currentUser);
        task.setDismissedAt(Instant.now());
        uploadTaskRepository.save(task);
        deleteTaskStorageObject(task.getStoredPath());
        return toUploadTaskResponse(task);
    }

    @Transactional
    public List<UploadTaskResponse> dismissUploadBatch(
            AuthenticatedUser currentUser,
            com.yingshi.server.dto.upload.UploadDismissBatchRequest request
    ) {
        UploadState state = parseOptionalState(request.state());
        String operationType = normalizeOperationType(request.operationType());
        Instant updatedAfter = Instant.now().minus(HISTORY_RETENTION);
        Instant dismissedAt = Instant.now();
        List<UploadTaskEntity> tasks = uploadTaskRepository.findVisibleHistory(
                currentUser.libraryId(),
                currentUser.userId(),
                updatedAfter,
                state,
                operationType
        );
        tasks.forEach(task -> {
            task.setDismissedAt(dismissedAt);
            deleteTaskStorageObject(task.getStoredPath());
        });
        uploadTaskRepository.saveAll(tasks);
        return tasks.stream().map(this::toUploadTaskResponse).toList();
    }

    @Transactional
    public void purgeExpiredTask(String taskId) {
        uploadTaskRepository.findById(taskId).ifPresent(task -> {
            deleteTaskStorageObject(task.getStoredPath());
            uploadTaskRepository.delete(task);
        });
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

    private void ensureTaskUploader(UploadTaskEntity task, AuthenticatedUser currentUser) {
        String uploadedByUserId = normalizeNullable(task.getUploadedByUserId());
        if (uploadedByUserId != null && !uploadedByUserId.equals(currentUser.userId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.UPLOAD_NOT_FOUND, "Upload task was not found.");
        }
    }

    private void deleteTaskStorageObject(String objectKey) {
        String normalized = ObjectKeyPolicy.tryNormalizeRelativeObjectKey(objectKey);
        if (normalized == null) {
            return;
        }
        try {
            objectStorageService.delete(normalized);
        } catch (Exception ignored) {
        }
    }

    private UploadTaskResponse toUploadTaskResponse(UploadTaskEntity task) {
        String objectKey = task.getStoredPath();
        if (objectKey == null && task.getMediaId() != null) {
            objectKey = "/api/media/files/" + task.getMediaId();
        }
        return new UploadTaskResponse(
                task.getId(),
                task.getFileName(),
                task.getMediaType().name().toLowerCase(Locale.ROOT),
                objectKey,
                task.getMediaId(),
                task.getState().name().toLowerCase(Locale.ROOT),
                switch (task.getState()) {
                    case WAITING -> 0;
                    case SUCCESS, FAILED, CANCELLED -> 100;
                },
                task.getErrorMessage(),
                task.getOperationId(),
                task.getOperationType(),
                task.getOperationTitle(),
                task.getOperationMediaCount(),
                task.getSourceItemId(),
                instantToMillis(task.getCreatedAt()),
                instantToMillis(task.getUpdatedAt()),
                instantToMillis(task.getCompletedAt()),
                taskMedia(task)
        );
    }

    private MediaDto taskMedia(UploadTaskEntity task) {
        String mediaId = normalizeNullable(task.getMediaId());
        if (mediaId == null || task.getState() != UploadState.SUCCESS) {
            return null;
        }
        return mediaRepository.findByIdAndLibraryId(mediaId, task.getLibraryId())
                .map(media -> contentMapper.toMediaDto(media, List.of()))
                .orElse(null);
    }

    private MediaEntity buildMediaFromTask(String mediaId, UploadTaskEntity task, LocalMediaStorageService.StoredFile storedFile) {
        String mediaUrl = "/api/media/files/" + mediaId;

        MediaEntity media = new MediaEntity();
        media.setId(mediaId);
        media.setLibraryId(task.getLibraryId());
        media.setMediaType(task.getMediaType());
        media.setUrl(mediaUrl);
        media.setPreviewUrl(task.getMediaType() == MediaType.IMAGE
                ? mediaUrl + "?variant=preview"
                : mediaUrl + "?variant=cover");
        media.setOriginalUrl(task.getMediaType() == MediaType.IMAGE ? mediaUrl : null);
        media.setVideoUrl(task.getMediaType() == MediaType.VIDEO ? mediaUrl : null);
        media.setCoverUrl(null);
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
        media.setStoragePath(storedFile.storagePath());
        media.setStorageProvider(storedFile.storageProvider());
        media.setBucket(storedFile.bucket());
        media.setOriginalObjectKey(storedFile.objectKey());
        media.setChecksum(storedFile.checksum());
        media.setSourceFingerprint(task.getSourceFingerprint());
        media.setUploadedByUserId(task.getUploadedByUserId());
        return media;
    }

    private LocalMediaStorageService.StoredFile storedFileFromMetadata(String objectKey, ObjectMetadata metadata) {
        return new LocalMediaStorageService.StoredFile(
                objectKey,
                storedFileName(objectKey),
                localMediaStorageService.provider(),
                localMediaStorageService.bucket(),
                metadata.objectKey(),
                metadata.checksum(),
                metadata.sizeBytes()
        );
    }

    private String normalizeConfirmObjectKey(UploadTaskEntity task, UploadConfirmRequest request) {
        String requestObjectKey = ObjectKeyPolicy.tryNormalizeRelativeObjectKey(request.objectKey());
        String taskObjectKey = ObjectKeyPolicy.tryNormalizeRelativeObjectKey(task.getStoredPath());
        String objectKey = requestObjectKey != null ? requestObjectKey : taskObjectKey;
        if (objectKey == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_OBJECT_INVALID, "objectKey is required for direct upload confirmation.");
        }
        if (taskObjectKey != null && !taskObjectKey.equals(objectKey)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_OBJECT_MISMATCH, "Uploaded object does not belong to this upload task.");
        }
        return objectKey;
    }

    private void validateUploadedObject(UploadTaskEntity task, ObjectMetadata metadata) {
        Long actualSizeBytes = metadata.sizeBytes();
        if (actualSizeBytes != null && !actualSizeBytes.equals(task.getFileSizeBytes())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_SIZE_MISMATCH, "Uploaded object size does not match the upload task.");
        }
        String actualContentType = normalizeMimeType(metadata.contentType());
        if (actualContentType != null
                && !"application/octet-stream".equals(actualContentType)
                && !sameContentType(actualContentType, task.getMimeType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_CONTENT_TYPE_MISMATCH, "Uploaded object content type does not match the upload task.");
        }
    }

    private boolean sameContentType(String first, String second) {
        String left = contentTypeWithoutParameters(first);
        String right = contentTypeWithoutParameters(second);
        return left != null && left.equals(right);
    }

    private String contentTypeWithoutParameters(String value) {
        String normalized = normalizeMimeType(value);
        if (normalized == null) {
            return null;
        }
        int semicolonIndex = normalized.indexOf(';');
        return semicolonIndex >= 0 ? normalized.substring(0, semicolonIndex).trim() : normalized;
    }

    private String storedFileName(String objectKey) {
        int slashIndex = objectKey.lastIndexOf('/');
        return slashIndex >= 0 ? objectKey.substring(slashIndex + 1) : objectKey;
    }

    private void warmPreviewIfPossible(MediaEntity media) {
        if (media.getMediaType() == MediaType.IMAGE) {
            if (!localMediaStorageService.ensureImagePreview(media.getStoragePath(), media.getId(), PREVIEW_MAX_DIMENSION)) {
                logger.warn("Image preview generation failed for media {} (type={}), serving original instead",
                        media.getId(), media.getMimeType());
                media.setPreviewUrl(media.getUrl());
                media.setPreviewObjectKey(null);
            } else {
                media.setPreviewObjectKey(localMediaStorageService.imagePreviewObjectKey(
                        media.getStoragePath(),
                        media.getId(),
                        PREVIEW_MAX_DIMENSION
                ));
            }
            return;
        }
        if (media.getMediaType() == MediaType.VIDEO) {
            media.setCoverUrl(media.getUrl() + "?variant=cover");
            String coverObjectKey = localMediaStorageService.videoCoverObjectKey(
                    media.getStoragePath(),
                    media.getId(),
                    PREVIEW_MAX_DIMENSION
            );
            if (localMediaStorageService.ensureVideoCover(
                    media.getStoragePath(),
                    media.getId(),
                    PREVIEW_MAX_DIMENSION
            )) {
                media.setCoverObjectKey(coverObjectKey);
                media.setPreviewObjectKey(coverObjectKey);
            } else {
                logger.warn("Video cover generation failed for media {} (type={}), cover will be unavailable",
                        media.getId(), media.getMimeType());
                media.setCoverObjectKey(null);
                media.setPreviewObjectKey(null);
            }
        }
    }

    private String normalizeSourceFingerprint(String rawFingerprint) {
        if (rawFingerprint == null || rawFingerprint.isBlank()) {
            return null;
        }
        return rawFingerprint.trim().toLowerCase(Locale.ROOT);
    }

    private UploadState parseOptionalState(String rawState) {
        String normalized = normalizeNullable(rawState);
        if (normalized == null) {
            return null;
        }
        try {
            return UploadState.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "state is invalid.");
        }
    }

    private String normalizeOperationType(String rawOperationType) {
        String normalized = normalizeNullable(rawOperationType);
        if (normalized == null) {
            return null;
        }
        String value = normalized.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "IMPORT_TO_APP", "CREATE_POST", "ADD_TO_EXISTING_POST" -> value;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "operationType is invalid.");
        };
    }

    private String normalizeBounded(String value, int maxLength) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private Long instantToMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    private String normalizeMimeType(String rawMimeType) {
        if (rawMimeType == null || rawMimeType.isBlank()) {
            return null;
        }
        return rawMimeType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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
