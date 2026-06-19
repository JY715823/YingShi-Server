package com.yingshi.server.repository;

import com.yingshi.server.domain.UploadTaskEntity;
import com.yingshi.server.domain.UploadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UploadTaskRepository extends JpaRepository<UploadTaskEntity, String> {

    Optional<UploadTaskEntity> findByIdAndLibraryId(String id, String libraryId);

    java.util.List<UploadTaskEntity> findByLibraryIdOrderByUpdatedAtDesc(String libraryId);

    @Query("""
            select task from UploadTaskEntity task
            where task.libraryId = :libraryId
              and task.uploadedByUserId = :uploadedByUserId
              and task.dismissedAt is null
              and task.updatedAt >= :updatedAfter
              and (:state is null or task.state = :state)
              and (:operationType is null or task.operationType = :operationType)
            order by task.updatedAt desc, task.id desc
            """)
    java.util.List<UploadTaskEntity> findVisibleHistory(
            @Param("libraryId") String libraryId,
            @Param("uploadedByUserId") String uploadedByUserId,
            @Param("updatedAfter") Instant updatedAfter,
            @Param("state") UploadState state,
            @Param("operationType") String operationType
    );

    List<UploadTaskEntity> findByStateAndExpireAtBeforeOrderByExpireAtAsc(UploadState state, Instant cutoff);
}
