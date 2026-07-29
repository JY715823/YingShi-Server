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

    List<TrashItemEntity> findByLibraryIdAndStateAndItemTypeAndSourcePostId(
            String libraryId,
            TrashItemState state,
            TrashItemType itemType,
            String sourcePostId
    );

    List<TrashItemEntity> findByLibraryIdOrderByUpdatedAtDesc(String libraryId);

    @Query("SELECT MAX(t.updatedAt) FROM TrashItemEntity t WHERE t.libraryId = :libraryId")
    Optional<Instant> findLatestUpdatedAtByLibraryId(@Param("libraryId") String libraryId);

    // P1-3 根因修复: photo 回收站版本 (lifeCategory IS NULL)，避免 life 回收站操作影响 trashVersion → notificationVersion
    @Query("SELECT MAX(t.updatedAt) FROM TrashItemEntity t WHERE t.libraryId = :libraryId AND t.lifeCategory IS NULL")
    Optional<Instant> findLatestUpdatedAtByLibraryIdAndLifeCategoryIsNull(@Param("libraryId") String libraryId);

    // P1-3 根因修复: life 回收站版本 (lifeCategory IS NOT NULL)，纳入 lifeConsoleVersion
    @Query("SELECT MAX(t.updatedAt) FROM TrashItemEntity t WHERE t.libraryId = :libraryId AND t.lifeCategory IS NOT NULL")
    Optional<Instant> findLatestUpdatedAtByLibraryIdAndLifeCategoryIsNotNull(@Param("libraryId") String libraryId);

    @Query("SELECT t FROM TrashItemEntity t WHERE t.state = :state AND t.undoDeadlineAt < :undoDeadlineAt ORDER BY t.undoDeadlineAt ASC")
    List<TrashItemEntity> findByStateAndUndoDeadlineAtBeforeOrderByUndoDeadlineAtAsc(@Param("state") TrashItemState state, @Param("undoDeadlineAt") Instant undoDeadlineAt);

    // P1-3 自动清理: 查询 IN_TRASH 状态超过指定时间的回收站项目(用于 30 天自动彻底删除)
    @Query("SELECT t FROM TrashItemEntity t WHERE t.state = :state AND t.deletedAt < :threshold ORDER BY t.deletedAt ASC")
    List<TrashItemEntity> findByStateAndDeletedAtBeforeOrderByDeletedAtAsc(@Param("state") TrashItemState state, @Param("threshold") Instant threshold);

    // P1-2 改造: life 回收站查询 — lifeCategory IS NOT NULL 表示 life 媒体回收站
    @Query("SELECT t FROM TrashItemEntity t WHERE t.libraryId = :libraryId AND t.state = :state AND t.lifeCategory IS NOT NULL ORDER BY t.deletedAt DESC")
    List<TrashItemEntity> findLifeTrashByLibraryIdAndState(@Param("libraryId") String libraryId, @Param("state") TrashItemState state);

    // P1-2 改造: life 回收站按 category 查询
    @Query("SELECT t FROM TrashItemEntity t WHERE t.libraryId = :libraryId AND t.state = :state AND t.lifeCategory = :lifeCategory ORDER BY t.deletedAt DESC")
    List<TrashItemEntity> findLifeTrashByLibraryIdAndStateAndCategory(
            @Param("libraryId") String libraryId,
            @Param("state") TrashItemState state,
            @Param("lifeCategory") String lifeCategory
    );

    // P1-2 改造: photo 回收站查询 — lifeCategory IS NULL 表示 photo 媒体回收站
    @Query("SELECT t FROM TrashItemEntity t WHERE t.libraryId = :libraryId AND t.state = :state AND t.lifeCategory IS NULL ORDER BY t.deletedAt DESC")
    List<TrashItemEntity> findPhotoTrashByLibraryIdAndState(@Param("libraryId") String libraryId, @Param("state") TrashItemState state);

    // R2-C-1 photo 回收站 keyset 分页 — SQL 层过滤 lifeCategory IS NULL，避免内存过滤。
    // cursor/cursorId 组成 (deletedAt DESC, id DESC) 的稳定 keyset，初始调用传 cursor=now, cursorId=null。
    @Query("SELECT t FROM TrashItemEntity t WHERE t.libraryId = :libraryId AND t.state = :state AND t.lifeCategory IS NULL "
            + "AND (t.deletedAt < :cursor OR (t.deletedAt = :cursor AND t.id < :cursorId)) "
            + "ORDER BY t.deletedAt DESC, t.id DESC")
    List<TrashItemEntity> findPhotoTrashBeforeCursor(
            @Param("libraryId") String libraryId,
            @Param("state") TrashItemState state,
            @Param("cursor") Instant cursor,
            @Param("cursorId") String cursorId,
            Pageable pageable);

    // R2-C-1 photo 回收站 keyset 分页 + itemType 过滤
    @Query("SELECT t FROM TrashItemEntity t WHERE t.libraryId = :libraryId AND t.state = :state AND t.lifeCategory IS NULL "
            + "AND t.itemType = :itemType "
            + "AND (t.deletedAt < :cursor OR (t.deletedAt = :cursor AND t.id < :cursorId)) "
            + "ORDER BY t.deletedAt DESC, t.id DESC")
    List<TrashItemEntity> findPhotoTrashBeforeCursorByItemType(
            @Param("libraryId") String libraryId,
            @Param("state") TrashItemState state,
            @Param("itemType") TrashItemType itemType,
            @Param("cursor") Instant cursor,
            @Param("cursorId") String cursorId,
            Pageable pageable);

    // R2-C-2 photo 回收站真实总数 — SQL 层过滤 lifeCategory IS NULL
    @Query("SELECT COUNT(t) FROM TrashItemEntity t WHERE t.libraryId = :libraryId AND t.state = :state AND t.lifeCategory IS NULL")
    long countPhotoTrash(@Param("libraryId") String libraryId, @Param("state") TrashItemState state);

    // R2-C-2 photo 回收站真实总数 + itemType 过滤
    @Query("SELECT COUNT(t) FROM TrashItemEntity t WHERE t.libraryId = :libraryId AND t.state = :state AND t.lifeCategory IS NULL AND t.itemType = :itemType")
    long countPhotoTrashByItemType(@Param("libraryId") String libraryId, @Param("state") TrashItemState state, @Param("itemType") TrashItemType itemType);

    // R2-C-5/6 life 回收站 keyset 分页 — lifeCategory IS NOT NULL（所有 life）
    @Query("SELECT t FROM TrashItemEntity t WHERE t.libraryId = :libraryId AND t.state = :state AND t.lifeCategory IS NOT NULL "
            + "AND (t.deletedAt < :cursor OR (t.deletedAt = :cursor AND t.id < :cursorId)) "
            + "ORDER BY t.deletedAt DESC, t.id DESC")
    List<TrashItemEntity> findLifeTrashBeforeCursor(
            @Param("libraryId") String libraryId,
            @Param("state") TrashItemState state,
            @Param("cursor") Instant cursor,
            @Param("cursorId") String cursorId,
            Pageable pageable);

    // R2-C-5/6 life 回收站 keyset 分页 — 按 category
    @Query("SELECT t FROM TrashItemEntity t WHERE t.libraryId = :libraryId AND t.state = :state AND t.lifeCategory = :lifeCategory "
            + "AND (t.deletedAt < :cursor OR (t.deletedAt = :cursor AND t.id < :cursorId)) "
            + "ORDER BY t.deletedAt DESC, t.id DESC")
    List<TrashItemEntity> findLifeTrashBeforeCursorByCategory(
            @Param("libraryId") String libraryId,
            @Param("state") TrashItemState state,
            @Param("lifeCategory") String lifeCategory,
            @Param("cursor") Instant cursor,
            @Param("cursorId") String cursorId,
            Pageable pageable);
}
