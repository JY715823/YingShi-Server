package com.yingshi.server.repository;

import com.yingshi.server.domain.TrashItemEntity;
import com.yingshi.server.domain.TrashItemState;
import com.yingshi.server.domain.TrashItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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

    List<TrashItemEntity> findByLibraryIdAndStateOrderByDeletedAtDesc(String libraryId, TrashItemState state);

    List<TrashItemEntity> findByLibraryIdAndStateAndItemType(
            String libraryId,
            TrashItemState state,
            TrashItemType itemType
    );

    List<TrashItemEntity> findByLibraryIdOrderByUpdatedAtDesc(String libraryId);

    List<TrashItemEntity> findByStateAndUndoDeadlineAtBeforeOrderByUndoDeadlineAtAsc(TrashItemState state, Instant undoDeadlineAt);
}
