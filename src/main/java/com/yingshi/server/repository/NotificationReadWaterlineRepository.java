package com.yingshi.server.repository;

import com.yingshi.server.domain.notification.NotificationReadWaterlineEntity;
import com.yingshi.server.domain.notification.NotificationReadWaterlineEntity.PK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * R2-A: Read waterline repository.
 *
 * <p>Tracks the last event each user has marked as read per library, so
 * {@code listNotifications} can determine read state with a single waterline
 * lookup instead of a per-notification query against {@code notification_reads}.
 *
 * <p>Separated from {@link NotificationReadRepository} because JPA repositories
 * are entity-typed; both are injected into {@code NotificationService}.
 */
@Repository
public interface NotificationReadWaterlineRepository
        extends JpaRepository<NotificationReadWaterlineEntity, PK> {

    /**
     * Find the waterline for a (userId, libraryId) pair.
     */
    Optional<NotificationReadWaterlineEntity> findByUserIdAndLibraryId(String userId, String libraryId);

    /**
     * R2-A: Upsert the waterline. Updates the existing row if present,
     * otherwise the caller should {@link #save} a new entity.
     *
     * @return number of rows updated (0 if the waterline did not exist)
     */
    @Modifying
    @Query("UPDATE NotificationReadWaterlineEntity w " +
            "SET w.lastReadEventId = :lastReadEventId, w.lastReadAt = :lastReadAt " +
            "WHERE w.userId = :userId AND w.libraryId = :libraryId")
    int updateWaterline(
            @Param("userId") String userId,
            @Param("libraryId") String libraryId,
            @Param("lastReadEventId") String lastReadEventId,
            @Param("lastReadAt") Instant lastReadAt);
}
