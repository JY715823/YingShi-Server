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
        UploadTaskEntity task = new UploadTaskEntity();
        task.setId(IdGenerator.newId("upload"));
        task.setSpaceId(currentUser.spaceId());
        task.setFileName(request.fileName().trim());
        task.setMediaType(parseMediaType(request.mediaType()));
        task.setMimeType(request.mimeType().trim());
        task.setFileSizeBytes(request.fileSizeBytes());
        task.setWidth(request.width());
        task.setHeight(request.height());
        task.setDurationMillis(request.durationMillis());
        task.setDisplayTimeMillis(request.displayTimeMillis());
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
        UploadTaskEntity task = requireUploadTask(uploadId, currentUser.spaceId());
        if (task.getState() != UploadState.WAITING) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.UPLOAD_ALREADY_COMPLETED, "Upload task has already been completed.");
        }
        if (task.getExpireAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_FILE_MISMATCH, "Upload task has expired.");
        }
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_FILE_MISMATCH, "Uploaded file must not be empty.");
        }
        if (file.getSize() != task.getFileSizeBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_FILE_MISMATCH, "Uploaded file size does not match the requested size.");
        }
        if (file.getContentType() != null && !file.getContentType().equalsIgnoreCase(task.getMimeType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_FILE_MISMATCH, "Uploaded file content type does not match the requested mimeType.");
        }

        LocalMediaStorageService.StoredFile storedFile = localMediaStorageService.store(
                currentUser.spaceId(),
                uploadId,
                task.getFileName(),
                file
        );

        MediaEntity media = buildMediaFromTask(task, storedFile.absolutePath());
        mediaRepository.save(media);

        task.setState(UploadState.SUCCESS);
        task.setCompletedAt(Instant.now());
        task.setStoredPath(storedFile.absolutePath());
        task.setMediaId(media.getId());
        uploadTaskRepository.save(task);

        MediaDto mediaDto = contentMapper.toMediaDto(media, List.of());
        return new UploadCompleteResponse(task.getId(), "success", mediaDto);
    }

    private UploadTaskEntity requireUploadTask(String uploadId, String spaceId) {
        return uploadTaskRepository.findByIdAndSpaceId(uploadId, spaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.UPLOAD_NOT_FOUND, "Upload task was not found."));
    }

    private MediaEntity buildMediaFromTask(UploadTaskEntity task, String storedPath) {
        String mediaId = IdGenerator.newId("media");
        String mediaUrl = "/api/media/files/" + mediaId;

        MediaEntity media = new MediaEntity();
        media.setId(mediaId);
        media.setSpaceId(task.getSpaceId());
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
        media.setStoragePath(storedPath);
        return media;
    }

    private MediaType parseMediaType(String mediaType) {
        try {
            return MediaType.valueOf(mediaType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "mediaType must be image or video.");
        }
    }
}
