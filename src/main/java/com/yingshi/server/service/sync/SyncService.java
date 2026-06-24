package com.yingshi.server.service.sync;

import com.yingshi.server.domain.AlbumEntity;
import com.yingshi.server.domain.BowelEventEntity;
import com.yingshi.server.domain.CommentEntity;
import com.yingshi.server.domain.LedgerSnapshotEntity;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.domain.TrashItemEntity;
import com.yingshi.server.domain.UploadTaskEntity;
import com.yingshi.server.dto.sync.SyncVersionsResponse;
import com.yingshi.server.repository.AlbumRepository;
import com.yingshi.server.repository.BowelEventRepository;
import com.yingshi.server.repository.CommentRepository;
import com.yingshi.server.repository.LedgerSnapshotRepository;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.repository.TrashItemRepository;
import com.yingshi.server.repository.UploadTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class SyncService {

    private final MediaRepository mediaRepository;
    private final PostRepository postRepository;
    private final AlbumRepository albumRepository;
    private final TrashItemRepository trashItemRepository;
    private final BowelEventRepository bowelEventRepository;
    private final PostMediaRepository postMediaRepository;
    private final LedgerSnapshotRepository ledgerSnapshotRepository;
    private final CommentRepository commentRepository;
    private final UploadTaskRepository uploadTaskRepository;

    public SyncService(
            MediaRepository mediaRepository,
            PostRepository postRepository,
            AlbumRepository albumRepository,
            TrashItemRepository trashItemRepository,
            BowelEventRepository bowelEventRepository,
            PostMediaRepository postMediaRepository,
            LedgerSnapshotRepository ledgerSnapshotRepository,
            CommentRepository commentRepository,
            UploadTaskRepository uploadTaskRepository
    ) {
        this.mediaRepository = mediaRepository;
        this.postRepository = postRepository;
        this.albumRepository = albumRepository;
        this.trashItemRepository = trashItemRepository;
        this.bowelEventRepository = bowelEventRepository;
        this.postMediaRepository = postMediaRepository;
        this.ledgerSnapshotRepository = ledgerSnapshotRepository;
        this.commentRepository = commentRepository;
        this.uploadTaskRepository = uploadTaskRepository;
    }

    @Transactional(readOnly = true)
    public SyncVersionsResponse getVersions(String libraryId) {
        Optional<Instant> latestCommentUpdatedAt = commentRepository.findFirstByLibraryIdOrderByUpdatedAtDesc(libraryId)
                .map(CommentEntity::getUpdatedAt);
        Optional<Instant> latestUploadTaskUpdatedAt = uploadTaskRepository.findFirstByLibraryIdOrderByUpdatedAtDesc(libraryId)
                .map(UploadTaskEntity::getUpdatedAt);
        long photoFeedVersion = maxEpochMillis(
                mediaRepository.findTopByLibraryIdAndDeletedAtIsNullOrderByUpdatedAtDesc(libraryId)
                        .map(MediaEntity::getUpdatedAt),
                mediaRepository.findTopByLibraryIdOrderByUpdatedAtDesc(libraryId)
                        .map(MediaEntity::getUpdatedAt)
        );
        long albumsVersion = maxEpochMillis(
                albumRepository.findTopByLibraryIdAndDeletedAtIsNullAndSystemKeyIsNullOrderByUpdatedAtDesc(libraryId)
                        .map(AlbumEntity::getUpdatedAt),
                albumRepository.findTopByLibraryIdAndSystemKeyIsNullOrderByUpdatedAtDesc(libraryId)
                        .map(AlbumEntity::getUpdatedAt),
                postRepository.findFirstByLibraryIdAndDeletedAtIsNullAndSystemKeyIsNullOrderByUpdatedAtDesc(libraryId)
                        .map(PostEntity::getUpdatedAt),
                postRepository.findFirstByLibraryIdAndSystemKeyIsNullOrderByUpdatedAtDesc(libraryId)
                        .map(PostEntity::getUpdatedAt),
                postMediaRepository.findFirstByLibraryIdOrderByUpdatedAtDesc(libraryId)
                        .map(PostMediaEntity::getUpdatedAt),
                latestCommentUpdatedAt
        );
        long trashVersion = trashItemRepository.findFirstByLibraryIdOrderByUpdatedAtDesc(libraryId)
                .map(TrashItemEntity::getUpdatedAt)
                .map(SyncService::toEpochMillis)
                .orElse(0L);
        long lifeConsoleVersion = maxEpochMillis(
                bowelEventRepository.findFirstByLibraryIdOrderByUpdatedAtDesc(libraryId)
                        .map(BowelEventEntity::getUpdatedAt),
                postRepository.findFirstByLibraryIdAndDeletedAtIsNullAndSystemKeyIsNotNullOrderByUpdatedAtDesc(libraryId)
                        .map(PostEntity::getUpdatedAt),
                ledgerSnapshotRepository.findFirstByLibraryIdOrderByUpdatedAtDesc(libraryId)
                        .map(LedgerSnapshotEntity::getUpdatedAt)
        );
        long notificationVersion = maxEpochMillis(
                Optional.of(Instant.ofEpochMilli(photoFeedVersion)),
                Optional.of(Instant.ofEpochMilli(albumsVersion)),
                Optional.of(Instant.ofEpochMilli(trashVersion)),
                Optional.of(Instant.ofEpochMilli(lifeConsoleVersion)),
                latestCommentUpdatedAt,
                latestUploadTaskUpdatedAt
        );
        return new SyncVersionsResponse(
                photoFeedVersion,
                albumsVersion,
                trashVersion,
                notificationVersion,
                lifeConsoleVersion,
                System.currentTimeMillis()
        );
    }

    @SafeVarargs
    private static long maxEpochMillis(Optional<Instant>... optionals) {
        long max = 0L;
        for (Optional<Instant> opt : optionals) {
            if (opt.isPresent() && opt.get() != null) {
                max = Math.max(max, opt.get().toEpochMilli());
            }
        }
        return max;
    }

    private static long toEpochMillis(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }
}
