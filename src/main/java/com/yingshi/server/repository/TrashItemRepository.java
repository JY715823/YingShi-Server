package com.yingshi.server.repository;

import com.yingshi.server.domain.TrashItemEntity;
import com.yingshi.server.domain.TrashItemState;
import com.yingshi.server.domain.TrashItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrashItemRepository extends JpaRepository<TrashItemEntity, String> {

    Optional<TrashItemEntity> findByIdAndSpaceId(String id, String spaceId);

    Page<TrashItemEntity> findBySpaceIdAndState(String spaceId, TrashItemState state, Pageable pageable);

    Page<TrashItemEntity> findBySpaceIdAndStateAndItemType(
            String spaceId,
            TrashItemState state,
            TrashItemType itemType,
            Pageable pageable
    );

    List<TrashItemEntity> findBySpaceIdAndStateOrderByDeletedAtDesc(String spaceId, TrashItemState state);
}
