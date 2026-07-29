package com.yingshi.server.service.upload;

import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.UploadState;
import com.yingshi.server.domain.UploadTaskEntity;
import com.yingshi.server.dto.content.MediaDto;
import com.yingshi.server.dto.upload.UploadDismissBatchRequest;
import com.yingshi.server.dto.upload.UploadTaskResponse;
import com.yingshi.server.mapper.ContentMapper;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.UploadTaskRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * FR-10: 从原 UploadService 拆出的历史与响应服务。
 * 承担 listUploadHistory / getUploadTask / dismissUpload / dismissUploadBatch 主流程，
 * 以及共享的响应构造方法 toUploadTaskResponse / taskMedia / ensureTaskUploader。
 * <p>
 * toUploadTaskResponse 为 package-private，供同包的 UploadFileService.confirmUpload 委托调用，
 * 避免在两个 Service 中重复定义响应映射逻辑。
 */
@Service
public class UploadHistoryService {

    private static final Duration HISTORY_RETENTION = Duration.ofDays(30);
    private static final int DEFAULT_HISTORY_PAGE_SIZE = 50;
    private static final int MAX_HISTORY_PAGE_SIZE = 200;

    private final UploadTaskRepository uploadTaskRepository;
    private final MediaRepository mediaRepository;
    private final ContentMapper contentMapper;
    private final UploadSupport uploadSupport;

    public UploadHistoryService(
            UploadTaskRepository uploadTaskRepository,
            MediaRepository mediaRepository,
            ContentMapper contentMapper,
            UploadSupport uploadSupport
    ) {
        this.uploadTaskRepository = uploadTaskRepository;
        this.mediaRepository = mediaRepository;
        this.contentMapper = contentMapper;
        this.uploadSupport = uploadSupport;
    }

    @Transactional(readOnly = true)
    public UploadHistoryResult listUploadHistory(
            AuthenticatedUser currentUser,
            String rawState,
            String rawOperationType,
            Integer requestedPageSize,
            String cursor
    ) {
        UploadState state = UploadSupport.parseOptionalState(rawState);
        String operationType = UploadSupport.normalizeOperationType(rawOperationType);
        int pageSize = requestedPageSize == null
                ? DEFAULT_HISTORY_PAGE_SIZE
                : Math.max(1, Math.min(requestedPageSize, MAX_HISTORY_PAGE_SIZE));
        Instant updatedAfter = Instant.now().minus(HISTORY_RETENTION);

        Instant cursorUpdatedAt = null;
        String cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            int colonIndex = cursor.lastIndexOf(':');
            if (colonIndex > 0) {
                try {
                    cursorUpdatedAt = Instant.ofEpochMilli(Long.parseLong(cursor.substring(0, colonIndex)));
                    cursorId = cursor.substring(colonIndex + 1);
                } catch (Exception ignored) {
                    // Invalid cursor — treat as no cursor (return first page)
                }
            }
        }

        PageRequest pageRequest = PageRequest.of(0, pageSize + 1);
        List<UploadTaskEntity> entities = cursorUpdatedAt != null
                ? uploadTaskRepository.findVisibleHistoryPage(
                        currentUser.libraryId(), currentUser.userId(), updatedAfter,
                        state, operationType, cursorUpdatedAt, cursorId, pageRequest)
                : uploadTaskRepository.findVisibleHistoryFirstPage(
                        currentUser.libraryId(), currentUser.userId(), updatedAfter,
                        state, operationType, pageRequest);
        boolean hasMore = entities.size() > pageSize;
        List<UploadTaskEntity> pageItems = hasMore
                ? entities.subList(0, pageSize)
                : entities;
        String nextCursor = null;
        if (hasMore && !pageItems.isEmpty()) {
            UploadTaskEntity last = pageItems.get(pageItems.size() - 1);
            nextCursor = last.getUpdatedAt().toEpochMilli() + ":" + last.getId();
        }
        List<UploadTaskResponse> items = pageItems.stream()
                .map(this::toUploadTaskResponse)
                .toList();
        return new UploadHistoryResult(items, nextCursor, hasMore);
    }

    public record UploadHistoryResult(
            List<UploadTaskResponse> items,
            String nextCursor,
            boolean hasMore
    ) {}

    @Transactional(readOnly = true)
    public UploadTaskResponse getUploadTask(String uploadId, AuthenticatedUser currentUser) {
        UploadTaskEntity task = uploadSupport.requireUploadTask(uploadId, currentUser.libraryId());
        return toUploadTaskResponse(task);
    }

    @Transactional
    public UploadTaskResponse dismissUpload(String uploadId, AuthenticatedUser currentUser) {
        UploadTaskEntity task = uploadSupport.requireUploadTask(uploadId, currentUser.libraryId());
        ensureTaskUploader(task, currentUser);
        task.setDismissedAt(Instant.now());
        uploadTaskRepository.save(task);
        uploadSupport.deleteTaskStorageObject(task.getStoredPath());
        return toUploadTaskResponse(task);
    }

    @Transactional
    public List<UploadTaskResponse> dismissUploadBatch(
            AuthenticatedUser currentUser,
            UploadDismissBatchRequest request
    ) {
        UploadState state = UploadSupport.parseOptionalState(request.state());
        String operationType = UploadSupport.normalizeOperationType(request.operationType());
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
            uploadSupport.deleteTaskStorageObject(task.getStoredPath());
        });
        uploadTaskRepository.saveAll(tasks);
        return tasks.stream().map(this::toUploadTaskResponse).toList();
    }

    /**
     * 将 UploadTaskEntity 映射为 API 响应 DTO。
     * package-private，供 UploadFileService.confirmUpload 委托调用。
     */
    UploadTaskResponse toUploadTaskResponse(UploadTaskEntity task) {
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
                UploadSupport.instantToMillis(task.getCreatedAt()),
                UploadSupport.instantToMillis(task.getUpdatedAt()),
                UploadSupport.instantToMillis(task.getCompletedAt()),
                taskMedia(task)
        );
    }

    private MediaDto taskMedia(UploadTaskEntity task) {
        String mediaId = UploadSupport.normalizeNullable(task.getMediaId());
        if (mediaId == null || task.getState() != UploadState.SUCCESS) {
            return null;
        }
        return mediaRepository.findByIdAndLibraryId(mediaId, task.getLibraryId())
                .map(media -> contentMapper.toMediaDto(media, List.of()))
                .orElse(null);
    }

    private void ensureTaskUploader(UploadTaskEntity task, AuthenticatedUser currentUser) {
        String uploadedByUserId = UploadSupport.normalizeNullable(task.getUploadedByUserId());
        if (uploadedByUserId != null && !uploadedByUserId.equals(currentUser.userId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.UPLOAD_NOT_FOUND, "Upload task was not found.");
        }
    }
}
