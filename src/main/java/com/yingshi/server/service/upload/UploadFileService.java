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
import com.yingshi.server.dto.upload.UploadConfirmRequest;
import com.yingshi.server.dto.upload.UploadTaskResponse;
import com.yingshi.server.mapper.ContentMapper;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.UploadTaskRepository;
import com.yingshi.server.service.geocoding.GeocodingService;
import com.yingshi.server.service.storage.ObjectKeyPolicy;
import com.yingshi.server.service.storage.ObjectMetadata;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * FR-10: 从原 UploadService 拆出的文件处理服务。
 * 承担 uploadFile / confirmUpload 主流程，以及文件处理相关私有方法：
 * findDuplicateMedia / ensureUploadCanComplete / markTaskSuccessIfWaiting / markFailedInNewTransaction (FR-1) /
 * buildMediaFromTask / storedFileFromMetadata / normalizeConfirmObjectKey / validateUploadedObject /
 * warmPreviewIfPossible / warmVideoCoverAsync。
 * 共享工具通过 {@link UploadSupport} 调用。
 */
@Service
public class UploadFileService {

    private static final int PREVIEW_MAX_DIMENSION = 1280;
    private static final Logger logger = LoggerFactory.getLogger(UploadFileService.class);

    private final UploadTaskRepository uploadTaskRepository;
    private final MediaRepository mediaRepository;
    private final ContentMapper contentMapper;
    private final LocalMediaStorageService localMediaStorageService;
    private final EntityManager entityManager;
    private final GeocodingService geocodingService;
    private final PlatformTransactionManager transactionManager;
    private final UploadNotificationService uploadNotificationService;
    private final UploadSupport uploadSupport;
    private final UploadHistoryService uploadHistoryService;

    public UploadFileService(
            UploadTaskRepository uploadTaskRepository,
            MediaRepository mediaRepository,
            ContentMapper contentMapper,
            LocalMediaStorageService localMediaStorageService,
            EntityManager entityManager,
            GeocodingService geocodingService,
            PlatformTransactionManager transactionManager,
            UploadNotificationService uploadNotificationService,
            UploadSupport uploadSupport,
            UploadHistoryService uploadHistoryService
    ) {
        this.uploadTaskRepository = uploadTaskRepository;
        this.mediaRepository = mediaRepository;
        this.contentMapper = contentMapper;
        this.localMediaStorageService = localMediaStorageService;
        this.entityManager = entityManager;
        this.geocodingService = geocodingService;
        this.transactionManager = transactionManager;
        this.uploadNotificationService = uploadNotificationService;
        this.uploadSupport = uploadSupport;
        this.uploadHistoryService = uploadHistoryService;
    }

    @Transactional
    public UploadCompleteResponse uploadFile(String uploadId, MultipartFile file, AuthenticatedUser currentUser) {
        UploadTaskEntity task = uploadSupport.requireUploadTask(uploadId, currentUser.libraryId());
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
        String actualContentType = UploadSupport.normalizeMimeType(file.getContentType());
        if (actualContentType != null && !"application/octet-stream".equals(actualContentType)) {
            task.setMimeType(actualContentType);
        }

        // R1-H-1: magic bytes 校验，防止伪造扩展名上传任意文件
        try {
            MagicBytesDetector.FileType detectedType = MagicBytesDetector.detect(file);
            if (!MagicBytesDetector.isAcceptable(detectedType, file.getContentType())) {
                markFailedInNewTransaction(task,
                        "File type mismatch: declared=" + file.getContentType() + " detected=" + detectedType);
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.UPLOAD_OBJECT_INVALID,
                        "File type not allowed or magic bytes mismatch");
            }
        } catch (IOException ioException) {
            markFailedInNewTransaction(task, "Failed to read magic bytes: " + ioException.getMessage());
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.UPLOAD_OBJECT_INVALID,
                    "Failed to read file header for magic bytes verification");
        }

        MediaEntity duplicateMedia = findDuplicateMedia(task);
        if (duplicateMedia != null) {
            ensureUploadCanComplete(task, null, null);
            markTaskSuccessIfWaiting(task, duplicateMedia.getStoragePath(), duplicateMedia.getId(), null, null);
            uploadNotificationService.notifyIfCompleted(currentUser.libraryId(), currentUser.userId(), task);
            MediaDto mediaDto = contentMapper.toMediaDto(duplicateMedia, List.of());
            return new UploadCompleteResponse(task.getId(), "success", mediaDto);
        }

        String mediaId = UploadSupport.normalizeNullable(task.getMediaId());
        if (mediaId == null) {
            mediaId = IdGenerator.newId("media");
        }
        try {
            LocalMediaStorageService.StoredFile storedFile = localMediaStorageService.storeOriginal(
                    mediaId,
                    task.getDisplayTimeMillis(),
                    task.getMediaType(),
                    task.getFileName(),
                    file,
                    task.getDomain() != null ? task.getDomain() : "photo"
            );
            ensureUploadCanComplete(task, storedFile.storagePath(), null);

            MediaEntity media = null;
            try {
                media = buildMediaFromTask(mediaId, task, storedFile);
                warmPreviewIfPossible(media);
                ensureUploadCanComplete(task, storedFile.storagePath(), null);
                mediaRepository.save(media);
                ensureUploadCanComplete(task, storedFile.storagePath(), media);
            } catch (Exception exception) {
                uploadSupport.deleteTaskStorageObject(storedFile.storagePath());
                if (media != null) {
                    uploadSupport.deleteTaskStorageObject(media.getPreviewObjectKey());
                    uploadSupport.deleteTaskStorageObject(media.getCoverObjectKey());
                }
                throw exception;
            }

            markTaskSuccessIfWaiting(task, storedFile.storagePath(), media.getId(), storedFile.storagePath(), media);
            uploadNotificationService.notifyIfCompleted(currentUser.libraryId(), currentUser.userId(), task);

            MediaDto mediaDto = contentMapper.toMediaDto(media, List.of());
            return new UploadCompleteResponse(task.getId(), "success", mediaDto);
        } catch (Exception ex) {
            markFailedInNewTransaction(task, ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public UploadTaskResponse confirmUpload(
            String uploadId,
            UploadConfirmRequest request,
            AuthenticatedUser currentUser
    ) {
        UploadTaskEntity task = uploadSupport.requireUploadTask(uploadId, currentUser.libraryId());
        if (task.getState() == UploadState.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.UPLOAD_ALREADY_COMPLETED, "Upload task has already been cancelled.");
        }
        if (task.getState() == UploadState.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.UPLOAD_ALREADY_COMPLETED, "Upload task has already failed.");
        }
        if (task.getState() == UploadState.SUCCESS) {
            return uploadHistoryService.toUploadTaskResponse(task);
        }
        if (task.getExpireAt().isBefore(Instant.now()) && task.getState() == UploadState.WAITING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_EXPIRED, "Upload task has expired.");
        }
        if (task.getState() == UploadState.WAITING) {
            try {
                String objectKey = normalizeConfirmObjectKey(task, request);
                ObjectMetadata metadata = localMediaStorageService.metadataForObjectKey(objectKey);
                if (metadata == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_OBJECT_INVALID, "Uploaded object was not found.");
                }
                validateUploadedObject(task, metadata);

                MediaEntity duplicateMedia = findDuplicateMedia(task);
                if (duplicateMedia != null) {
                    ensureUploadCanComplete(task, objectKey, null);
                    uploadSupport.deleteTaskStorageObject(objectKey);
                    markTaskSuccessIfWaiting(task, duplicateMedia.getStoragePath(), duplicateMedia.getId(), null, null);
                    uploadNotificationService.notifyIfCompleted(currentUser.libraryId(), currentUser.userId(), task);
                    return uploadHistoryService.toUploadTaskResponse(task);
                }

                String mediaId = UploadSupport.normalizeNullable(task.getMediaId());
                if (mediaId == null) {
                    mediaId = IdGenerator.newId("media");
                }
                LocalMediaStorageService.StoredFile storedFile = storedFileFromMetadata(objectKey, metadata);
                ensureUploadCanComplete(task, objectKey, null);
                MediaEntity media = null;
                try {
                    media = buildMediaFromTask(mediaId, task, storedFile);
                    warmPreviewIfPossible(media);
                    ensureUploadCanComplete(task, objectKey, null);
                    mediaRepository.save(media);
                    ensureUploadCanComplete(task, objectKey, media);
                } catch (Exception exception) {
                    uploadSupport.deleteTaskStorageObject(objectKey);
                    if (media != null) {
                        uploadSupport.deleteTaskStorageObject(media.getPreviewObjectKey());
                        uploadSupport.deleteTaskStorageObject(media.getCoverObjectKey());
                    }
                    throw exception;
                }

                markTaskSuccessIfWaiting(task, storedFile.storagePath(), media.getId(), objectKey, media);
                uploadNotificationService.notifyIfCompleted(currentUser.libraryId(), currentUser.userId(), task);
            } catch (Exception ex) {
                markFailedInNewTransaction(task, ex.getMessage());
                throw ex;
            }
        }
        return uploadHistoryService.toUploadTaskResponse(task);
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
                    .findDuplicateWithoutDuration(
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
                .findDuplicateWithDuration(
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

    private void ensureUploadCanComplete(
            UploadTaskEntity task,
            String uploadedObjectKey,
            MediaEntity persistedMedia
    ) {
        entityManager.flush();
        entityManager.refresh(task);
        if (task.getState() == UploadState.WAITING) {
            return;
        }
        uploadSupport.deleteTaskStorageObject(uploadedObjectKey);
        if (persistedMedia != null) {
            uploadSupport.deleteTaskStorageObject(persistedMedia.getStoragePath());
            uploadSupport.deleteTaskStorageObject(persistedMedia.getPreviewObjectKey());
            uploadSupport.deleteTaskStorageObject(persistedMedia.getCoverObjectKey());
        }
        if (task.getState() == UploadState.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.UPLOAD_ALREADY_COMPLETED, "Upload task has already been cancelled.");
        }
        if (task.getState() == UploadState.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.UPLOAD_ALREADY_COMPLETED, "Upload task has already failed.");
        }
        throw new ApiException(HttpStatus.CONFLICT, ErrorCode.UPLOAD_ALREADY_COMPLETED, "Upload task has already been completed.");
    }

    private void markTaskSuccessIfWaiting(
            UploadTaskEntity task,
            String storedPath,
            String mediaId,
            String uploadedObjectKey,
            MediaEntity persistedMedia
    ) {
        Instant completedAt = Instant.now();
        int updatedCount = uploadTaskRepository.markSuccessIfWaiting(
                task.getId(),
                task.getLibraryId(),
                UploadState.SUCCESS,
                UploadState.WAITING,
                completedAt,
                storedPath,
                mediaId
        );
        if (updatedCount != 1) {
            ensureUploadCanComplete(task, uploadedObjectKey, persistedMedia);
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.UPLOAD_ALREADY_COMPLETED, "Upload task has already been completed.");
        }
        task.setState(UploadState.SUCCESS);
        task.setCompletedAt(completedAt);
        task.setStoredPath(storedPath);
        task.setMediaId(mediaId);
    }

    /**
     * FR-1: 在独立事务中标记上传任务为 FAILED。
     * 使用 REQUIRES_NEW 传播级别，确保即使外层 @Transactional 事务回滚，
     * FAILED 状态也能被独立提交，让客户端立即看到失败状态。
     * 条件 UPDATE WHERE state = WAITING 自动跳过已终态的任务（CANCELLED/SUCCESS/FAILED），
     * 与取消竞态不冲突。
     */
    private void markFailedInNewTransaction(UploadTaskEntity task, String errorMessage) {
        String rawMessage = errorMessage == null ? "Upload failed." : errorMessage;
        final String safeMessage = rawMessage.length() > 500 ? rawMessage.substring(0, 500) : rawMessage;
        Instant completedAt = Instant.now();
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(status -> {
                int updatedCount = uploadTaskRepository.markFailedIfWaiting(
                        task.getId(),
                        task.getLibraryId(),
                        UploadState.FAILED,
                        UploadState.WAITING,
                        completedAt,
                        safeMessage
                );
                if (updatedCount != 1) {
                    logger.warn("markFailedIfWaiting did not update task {} (state may have changed concurrently)", task.getId());
                }
            });
        } catch (Exception ex) {
            logger.warn("Failed to mark task {} as FAILED: {}", task.getId(), ex.getMessage());
        }
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
        media.setDomain(task.getDomain() != null ? task.getDomain() : "photo");
        // life 模块分类从上传任务转移到 media，使 life 媒体可在不依赖 album/post 的情况下被查询到
        media.setLifeCategory(task.getLifeCategory());
        media.setUploadedByUserId(task.getUploadedByUserId());
        // FR-18: transfer location fields from upload task, fall back to server-side reverse geocoding when label missing
        Double lat = task.getLatitude();
        Double lng = task.getLongitude();
        String label = task.getLocationLabel();
        if (lat != null && lng != null && (label == null || label.isBlank())) {
            try {
                label = geocodingService.reverseGeocode(lat, lng);
            } catch (Exception ex) {
                logger.warn("Geocoding failed for upload task {}: {}", task.getId(), ex.getMessage());
            }
        }
        media.setLatitude(lat);
        media.setLongitude(lng);
        media.setLocationLabel(label);
        // R1-H-2: 真实图片尺寸/像素校验，防止解压炸弹和元数据造假
        if (task.getMediaType() == MediaType.IMAGE) {
            try {
                int declaredWidth = task.getWidth() == null ? 0 : task.getWidth();
                int declaredHeight = task.getHeight() == null ? 0 : task.getHeight();
                if ("local".equals(storedFile.storageProvider())) {
                    MediaMetadataProbe.verifyImage(Paths.get(storedFile.storagePath()),
                            declaredWidth, declaredHeight);
                } else {
                    // S3/COS: download from object storage and verify via InputStream
                    try (java.io.InputStream is = localMediaStorageService
                            .loadObject(storedFile.objectKey()).getInputStream()) {
                        MediaMetadataProbe.verifyImage(is, declaredWidth, declaredHeight);
                    }
                }
            } catch (IOException e) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.UPLOAD_OBJECT_INVALID,
                        "Image metadata verification failed: " + e.getMessage());
            }
        }
        return media;
    }

    private LocalMediaStorageService.StoredFile storedFileFromMetadata(String objectKey, ObjectMetadata metadata) {
        return new LocalMediaStorageService.StoredFile(
                objectKey,
                UploadSupport.storedFileName(objectKey),
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
        String actualContentType = UploadSupport.normalizeMimeType(metadata.contentType());
        if (actualContentType != null
                && !"application/octet-stream".equals(actualContentType)
                && !UploadSupport.sameContentType(actualContentType, task.getMimeType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_CONTENT_TYPE_MISMATCH, "Uploaded object content type does not match the upload task.");
        }
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
            media.setCoverObjectKey(coverObjectKey);
            media.setPreviewObjectKey(coverObjectKey);
            warmVideoCoverAsync(media.getStoragePath(), media.getId(), media.getMimeType());
        }
    }

    private void warmVideoCoverAsync(String storagePath, String mediaId, String mimeType) {
        CompletableFuture.runAsync(() -> {
            try {
                if (!localMediaStorageService.ensureVideoCover(storagePath, mediaId, PREVIEW_MAX_DIMENSION)) {
                    logger.warn("Video cover generation failed for media {} (type={}), cover will be generated lazily if possible",
                            mediaId, mimeType);
                }
            } catch (Exception exception) {
                logger.warn("Video cover warmup failed for media {} (type={})", mediaId, mimeType, exception);
            }
        });
    }
}
