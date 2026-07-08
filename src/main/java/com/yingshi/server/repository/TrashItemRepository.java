package com.yingshi.server.repository;

import com.yingshi.server.domain.TrashItemEntity;
import com.yingshi.server.domain.TrashItemState;
import com.yingshi.server.domain.TrashItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TrashItemRepository extends JpaRepository<TrashItemEntity, String> {

    Optional<TrashItemEntity> findByIdAndLibraryId(String id, String libraryId);

    Page<TrashItemEntity> findByLibraryIdAndState(String libraryId, TrashItemState state, Pageable pageable);

    Page<TrashItemEntity> findByLibraryIdAndStateAndItemType(
            String libraryId,
            TrashItemState state,
            TrashItemType itemType,
            Pageable pageable
    );

    @Query("SELECT t FROM TrashItemEntity t WHERE t.libraryId = :libraryId AND t.state = :state ORDER BY t.deletedAt DESC")
    List<TrashItemEntity> findByLibraryIdAndStateOrderByDeletedAtDesc(@Param("libraryId") String libraryId, @Param("state") TrashItemState state);

    List<TrashItemEntity> findByLibraryIdAndStateAndItemType(
            String libraryId,
            TrashItemState state,
            TrashItemType itemType
    );

    List<TrashItemEntity> findByLibraryIdOrderByUpdatedAtDesc(String libraryId);

    @Query("SELECT MAX(t.updatedAt) FROM TrashItemEntity t WHERE t.libraryId = :libraryId")
    Optional<Instant> findLatestUpdatedAtByLibraryId(@Param("libraryId") String libraryId);

    @Query("SELECT t FROM TrashItemEntity t WHERE t.state = :state AND t.undoDeadlineAt < :undoDeadlineAt ORDER BY t.undoDeadlineAt ASC")
    List<TrashItemEntity> findByStateAndUndoDeadlineAtBeforeOrderByUndoDeadlineAtAsc(@Param("state") TrashItemState state, @Param("undoDeadlineAt") Instant undoDeadlineAt);
}
