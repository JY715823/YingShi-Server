package com.yingshi.server.repository;

import com.yingshi.server.domain.notification.NotificationEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * R2-A-1: Persistent notification event repository.
 *
 * Backs the SSE replay mechanism and the cursor-paginated notification list
 * endpoint. Replaces the in-memory ring buffer that was lost on server restart.
 */
@Repository
public interface NotificationEventRepository extends JpaRepository<NotificationEventEntity, Long> {

    /**
     * Find events for a library+user created after the given instant,
     * newest first. Used for SSE replay (after a server-side cutoff) and
     * for cursor pagination on the list endpoint.
     */
    List<NotificationEventEntity> findByLibraryIdAndUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
            String libraryId, String userId, Instant after, Pageable pageable);

    /**
     * Find events for a library+user, newest first, regardless of cutoff.
     * Used for the initial notification list fetch (no cursor).
     */
    List<NotificationEventEntity> findByLibraryIdAndUserIdOrderByCreatedAtDesc(
            String libraryId, String userId, Pageable pageable);

    /**
     * Locate a single event by its business key (eventId, libraryId, userId).
     * Used for idempotent writes (skip if already exists).
     */
    Optional<NotificationEventEntity> findByEventIdAndLibraryIdAndUserId(
            String eventId, String libraryId, String userId);

    /**
     * R2-A-1: Delete expired events. Called by the retention scheduler.
     */
    @Modifying
    @Query("DELETE FROM NotificationEventEntity e WHERE e.expiresAt < :cutoff")
    int deleteExpired(Instant cutoff);

    /**
     * R2-A-1: Find events for a library with database id greater than the
     * given lastEventId, oldest first (so replay sends them in order).
     *
     * <p>Used by SSE replay after a reconnect: the client sends the last
     * SSE event id it received (which is now the event table BIGSERIAL id),
     * and the server replays all events with a higher id.
     *
     * <p>Survives server restart because the BIGSERIAL id is persistent
     * (unlike the previous in-memory AtomicLong counter which was reset).
     */
    List<NotificationEventEntity> findByLibraryIdAndIdGreaterThanOrderByIdAsc(
            String libraryId, Long id);

    /**
     * R2-A-1: Find all events for a library, newest first (for initial list
     * fetch when no cursor is provided).
     */
    List<NotificationEventEntity> findByLibraryIdOrderByIdDesc(
            String libraryId, Pageable pageable);

    /**
     * R2-A: Locate events by (libraryId, eventId) for cursor-based pagination.
     *
     * <p>The cursor sent by the client is {@code createdAtMillis:notificationId},
     * where {@code notificationId} corresponds to {@code event_id} in this table.
     * This lookup resolves the business event id to the BIGSERIAL {@code id}
     * used for efficient DB-level pagination.
     *
     * <p>May return multiple rows for the same (libraryId, eventId) because the
     * unique key is (event_id, library_id, user_id); callers take the first row.
     */
    List<NotificationEventEntity> findByLibraryIdAndEventId(String libraryId, String eventId);

    /**
     * R2-A: Cursor pagination — fetch events strictly older than the given
     * BIGSERIAL id, newest first. Used after resolving the cursor's event_id
     * to its persistent id via {@link #findByLibraryIdAndEventId}.
     */
    List<NotificationEventEntity> findByLibraryIdAndIdLessThanOrderByIdDesc(
            String libraryId, Long id, Pageable pageable);

    /**
     * R2-A: Fallback cursor pagination by timestamp, used when the cursor event
     * has been purged (retention) and cannot be located by event_id.
     */
    List<NotificationEventEntity> findByLibraryIdAndCreatedAtBeforeOrderByCreatedAtDescIdDesc(
            String libraryId, Instant before, Pageable pageable);

    /**
     * R2-A: Find the maximum BIGSERIAL id for a library. Used by markAllRead to
     * advance the read waterline to the newest event.
     */
    @Query("SELECT MAX(e.id) FROM NotificationEventEntity e WHERE e.libraryId = :libraryId")
    Long findMaxIdByLibraryId(@Param("libraryId") String libraryId);
}
