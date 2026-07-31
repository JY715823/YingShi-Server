package com.yingshi.server.service.upload;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.config.StorageProperties;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.domain.UploadState;
import com.yingshi.server.domain.UploadTaskEntity;
import com.yingshi.server.dto.upload.UploadTokenRequest;
import com.yingshi.server.dto.upload.UploadTokenResponse;
import com.yingshi.server.repository.UploadTaskRepository;
import com.yingshi.server.repository.SharedLibraryRepository;
import com.yingshi.server.service.storage.PresignedObjectUrl;
import com.yingshi.server.service.storage.ObjectStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * FR-10: 从原 UploadService 拆出的 Token 创建服务。
 * 承担 createUploadToken 全部逻辑：构造 UploadTaskEntity、生成 objectKey、
 * 根据 directUpload 开关返回 presigned-put 或 multipart 上传 URL。
 */
@Service
public class UploadTokenService {

    private static final Duration UPLOAD_TTL = Duration.ofMinutes(30);

    private final UploadTaskRepository uploadTaskRepository;
    private final SharedLibraryRepository sharedLibraryRepository;
    private final LocalMediaStorageService localMediaStorageService;
    private final ObjectStorageService objectStorageService;
    private final StorageProperties storageProperties;
    private final UploadQuotaService uploadQuotaService;

    public UploadTokenService(
            UploadTaskRepository uploadTaskRepository,
            SharedLibraryRepository sharedLibraryRepository,
            LocalMediaStorageService localMediaStorageService,
            ObjectStorageService objectStorageService,
            StorageProperties storageProperties,
            UploadQuotaService uploadQuotaService
    ) {
        this.uploadTaskRepository = uploadTaskRepository;
        this.sharedLibraryRepository = sharedLibraryRepository;
        this.localMediaStorageService = localMediaStorageService;
        this.objectStorageService = objectStorageService;
        this.storageProperties = storageProperties;
        this.uploadQuotaService = uploadQuotaService;
    }

    @Transactional
    public UploadTokenResponse createUploadToken(UploadTokenRequest request, AuthenticatedUser currentUser) {
        long nowMillis = Instant.now().toEpochMilli();
        String idempotencyKey = UploadSupport.normalizeBounded(request.idempotencyKey(), 128);
        if (idempotencyKey != null) {
            // Serialize token creation for one shared library. This closes the
            // check-then-insert race before the database unique index is hit.
            sharedLibraryRepository.findByIdForUpdate(currentUser.libraryId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                            "Shared library not found."));
            Optional<UploadTaskEntity> existing = uploadTaskRepository
                    .findByIdempotencyKeyAndLibraryIdAndUploadedByUserId(
                            idempotencyKey, currentUser.libraryId(), currentUser.userId());
            if (existing.isPresent()) {
                UploadTaskEntity task = existing.get();
                if (task.getExpireAt() != null && task.getExpireAt().isBefore(Instant.now())
                        && task.getState() == UploadState.WAITING) {
                    task.setIdempotencyKey(null);
                    uploadTaskRepository.save(task);
                } else {
                    return toUploadTokenResponse(task);
                }
            }
        }
        Long capturedAtMillis = request.capturedAtMillis();
        long importedAtMillis = request.importedAtMillis() != null ? request.importedAtMillis() : nowMillis;
        long displayTimeMillis = request.displayTimeMillis() != null
                ? request.displayTimeMillis()
                : (capturedAtMillis != null ? capturedAtMillis : importedAtMillis);
        String displayTimeSource = UploadSupport.normalizeDisplayTimeSource(
                request.displayTimeSource(),
                capturedAtMillis != null ? "ORIGINAL" : "IMPORTED"
        );

        MediaType mediaType = UploadSupport.parseMediaType(request.mediaType());
        // R1-H-3: 配额校验（单文件大小 + library 总量）
        uploadQuotaService.checkQuota(currentUser.libraryId(), mediaType.name(), request.fileSizeBytes());
        String mediaId = IdGenerator.newId("media");
        String domain = request.domain() != null && !request.domain().isBlank() ? request.domain().trim() : "photo";
        String objectKey = localMediaStorageService.originalObjectKeyForUpload(
                mediaId,
                displayTimeMillis,
                mediaType,
                request.fileName(),
                domain
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
        task.setSourceFingerprint(UploadSupport.normalizeSourceFingerprint(request.sourceFingerprint()));
        task.setUploadedByUserId(currentUser.userId());
        task.setOperationId(UploadSupport.normalizeBounded(request.operationId(), 255));
        task.setOperationType(UploadSupport.normalizeOperationType(request.operationType()));
        task.setOperationTitle(UploadSupport.normalizeBounded(request.operationTitle(), 255));
        task.setOperationMediaCount(request.operationMediaCount());
        task.setSourceItemId(UploadSupport.normalizeBounded(request.sourceItemId(), 255));
        task.setDomain(domain);
        // life 模块分类持久化：从请求中取 lifeCategory（PERSON/MEAL/null），
        // 后续在 buildMediaFromTask 中转移到 MediaEntity.lifeCategory，
        // 使 life 媒体不再依赖 album/post/post_media 关联体系。
        String lifeCategory = UploadSupport.normalizeBounded(request.lifeCategory(), 20);
        if (lifeCategory != null && !"life".equals(domain)) {
            // 防御性约束：lifeCategory 只能搭配 domain=life
            lifeCategory = null;
        }
        task.setLifeCategory(lifeCategory);
        task.setState(UploadState.WAITING);
        task.setExpireAt(Instant.now().plus(UPLOAD_TTL));
        // FR-18: persist location fields on the upload task so they can be transferred to MediaEntity later
        task.setLatitude(request.latitude());
        task.setLongitude(request.longitude());
        task.setLocationLabel(request.locationLabel());
        // V49: 客户端提取的全部EXIF元数据，传递给MediaEntity
        task.setExifMetadata(request.exifMetadata());
        task.setIdempotencyKey(idempotencyKey);
        // mediaId is not set here to avoid FK constraint violation on upload_tasks.media_id → media(id).
        // The media entity is created during file upload or confirmation, where mediaId is resolved
        // from task.getMediaId() (null → generated) — see uploadFile() and confirmUpload().
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
            return new UploadTokenResponse(task.getId(), localMediaStorageService.provider(), presignedUrl.url(),
                    presignedUrl.expiresAtMillis(), task.getState().name().toLowerCase(Locale.ROOT),
                    "presigned-put", objectKey, presignedUrl.headers(), "/api/uploads/" + task.getId() + "/confirm");
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

    private UploadTokenResponse toUploadTokenResponse(UploadTaskEntity task) {
        boolean directUpload = task.getStoredPath() != null
                && !task.getStoredPath().isBlank()
                && storageProperties.directUploadEnabled()
                && objectStorageService.supportsPresignedPut()
                && objectStorageService.isDirectUploadAvailable();
        if (directUpload) {
            PresignedObjectUrl presignedUrl = objectStorageService.presignPut(
                    task.getStoredPath(), task.getMimeType(), task.getFileSizeBytes(), storageProperties.signedUrlTtl()
            ).orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.UPLOAD_STORAGE_ERROR, "Storage provider does not support direct upload."));
            return new UploadTokenResponse(task.getId(), localMediaStorageService.provider(), presignedUrl.url(),
                    presignedUrl.expiresAtMillis(), task.getState().name().toLowerCase(Locale.ROOT),
                    "presigned-put", task.getStoredPath(), presignedUrl.headers(),
                    "/api/uploads/" + task.getId() + "/confirm");
        }
        return new UploadTokenResponse(task.getId(), localMediaStorageService.provider(),
                "/api/uploads/" + task.getId() + "/file", task.getExpireAt().toEpochMilli(),
                task.getState().name().toLowerCase(Locale.ROOT), "multipart", null, Map.of(),
                "/api/uploads/" + task.getId() + "/confirm");
    }
}
