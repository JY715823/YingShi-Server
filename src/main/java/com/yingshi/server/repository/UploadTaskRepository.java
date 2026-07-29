package com.yingshi.server.repository;

import com.yingshi.server.domain.UploadTaskEntity;
import com.yingshi.server.domain.UploadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UploadTaskRepository extends JpaRepository<UploadTaskEntity, String> {

    Optional<UploadTaskEntity> findByIdAndLibraryId(String id, String libraryId);

    java.util.List<UploadTaskEntity> findByLibraryIdOrderByUpdatedAtDesc(String libraryId);

    Optional<UploadTaskEntity> findFirstByLibraryIdOrderByUpdatedAtDesc(String libraryId);

    java.util.List<UploadTaskEntity> findByLibraryIdAndOperationId(String libraryId, String operationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UploadTaskEntity task
            set task.state = :successState,
                task.completedAt = :completedAt,
                task.storedPath = :storedPath,
                task.mediaId = :mediaId,
                task.errorMessage = null
            where task.id = :id
              and task.libraryId = :libraryId
              and task.state = :waitingState
            """)
    int markSuccessIfWaiting(
            @Param("id") String id,
            @Param("libraryId") String libraryId,
            @Param("successState") UploadState successState,
            @Param("waitingState") UploadState waitingState,
            @Param("completedAt") Instant completedAt,
            @Param("storedPath") String storedPath,
            @Param("mediaId") String mediaId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UploadTaskEntity task
            set task.state = :failedState,
                task.completedAt = :completedAt,
                task.errorMessage = :errorMessage
            where task.id = :id
              and task.libraryId = :libraryId
              and task.state = :waitingState
            """)
    int markFailedIfWaiting(
            @Param("id") String id,
            @Param("libraryId") String libraryId,
            @Param("failedState") UploadState failedState,
            @Param("waitingState") UploadState waitingState,
            @Param("completedAt") Instant completedAt,
            @Param("errorMessage") String errorMessage
    );

    @Query("""
            select task from UploadTaskEntity task
            where task.libraryId = :libraryId
              and task.uploadedByUserId = :uploadedByUserId
              and task.dismissedAt is null
              and task.updatedAt >= :updatedAfter
              and (:state = :state or task.state = :state)
              and (:operationType = :operationType or task.operationType = :operationType)
            order by task.updatedAt desc, task.id desc
            """)
    java.util.List<UploadTaskEntity> findVisibleHistory(
            @Param("libraryId") String libraryId,
            @Param("uploadedByUserId") String uploadedByUserId,
            @Param("updatedAfter") Instant updatedAfter,
            @Param("state") UploadState state,
            @Param("operationType") String operationType
    );

    // Split: first page without cursor — avoids PostgreSQL "could not determine
    // data type of parameter" on (:cursor IS NULL OR ...) pattern.
    @Query("""
            select task from UploadTaskEntity task
            where task.libraryId = :libraryId
              and task.uploadedByUserId = :uploadedByUserId
              and task.dismissedAt is null
              and task.updatedAt >= :updatedAfter
              and (:state = :state or task.state = :state)
              and (:operationType = :operationType or task.operationType = :operationType)
            order by task.updatedAt desc, task.id desc
            """)
    java.util.List<UploadTaskEntity> findVisibleHistoryFirstPage(
            @Param("libraryId") String libraryId,
            @Param("uploadedByUserId") String uploadedByUserId,
            @Param("updatedAfter") Instant updatedAfter,
            @Param("state") UploadState state,
            @Param("operationType") String operationType,
            Pageable pageable
    );

    @Query("""
            select task from UploadTaskEntity task
            where task.libraryId = :libraryId
              and task.uploadedByUserId = :uploadedByUserId
              and task.dismissedAt is null
              and task.updatedAt >= :updatedAfter
              and (:state = :state or task.state = :state)
              and (:operationType = :operationType or task.operationType = :operationType)
              and (task.updatedAt < :cursorUpdatedAt or (task.updatedAt = :cursorUpdatedAt and task.id < :cursorId))
            order by task.updatedAt desc, task.id desc
            """)
    java.util.List<UploadTaskEntity> findVisibleHistoryPage(
            @Param("libraryId") String libraryId,
            @Param("uploadedByUserId") String uploadedByUserId,
            @Param("updatedAfter") Instant updatedAfter,
            @Param("state") UploadState state,
            @Param("operationType") String operationType,
            @Param("cursorUpdatedAt") Instant cursorUpdatedAt,
            @Param("cursorId") String cursorId,
            Pageable pageable
    );

    @Query("SELECT MAX(t.updatedAt) FROM UploadTaskEntity t WHERE t.libraryId = :libraryId")
    Optional<Instant> findLatestUpdatedAtByLibraryId(@Param("libraryId") String libraryId);

    // 排除 life domain 的 upload_task，避免 life 上传任务影响 notificationVersion
    // （life 上传任务的变化已通过 lifeConsoleVersion 反映，无需再通过 notificationVersion 重复触发）
    @Query("SELECT MAX(t.updatedAt) FROM UploadTaskEntity t WHERE t.libraryId = :libraryId AND (t.domain IS NULL OR t.domain != 'life')")
    Optional<Instant> findLatestUpdatedAtByLibraryIdAndDomainNotLife(@Param("libraryId") String libraryId);

    List<UploadTaskEntity> findByStateAndExpireAtBeforeOrderByExpireAtAsc(UploadState state, Instant cutoff);

    /** R3-DATA-003: Find existing task by idempotency key for duplicate prevention. */
    Optional<UploadTaskEntity> findByIdempotencyKeyAndLibraryIdAndUploadedByUserId(
            String idempotencyKey, String libraryId, String uploadedByUserId);
}
