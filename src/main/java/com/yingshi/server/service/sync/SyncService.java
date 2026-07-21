package com.yingshi.server.service.sync;

import com.yingshi.server.repository.AlbumRepository;
import com.yingshi.server.repository.BowelEventRepository;
import com.yingshi.server.repository.CommentRepository;
import com.yingshi.server.repository.LedgerSnapshotRepository;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.repository.TrashItemRepository;
import com.yingshi.server.repository.UploadTaskRepository;
import com.yingshi.server.repository.chat.ImportedChatRepository;
import com.yingshi.server.dto.sync.SyncVersionsResponse;
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
    private final ImportedChatRepository importedChatRepository;

    public SyncService(
            MediaRepository mediaRepository,
            PostRepository postRepository,
            AlbumRepository albumRepository,
            TrashItemRepository trashItemRepository,
            BowelEventRepository bowelEventRepository,
            PostMediaRepository postMediaRepository,
            LedgerSnapshotRepository ledgerSnapshotRepository,
            CommentRepository commentRepository,
            UploadTaskRepository uploadTaskRepository,
            ImportedChatRepository importedChatRepository
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
        this.importedChatRepository = importedChatRepository;
    }

    @Transactional(readOnly = true)
    public SyncVersionsResponse getVersions(String libraryId) {
        Optional<Instant> latestComment = commentRepository.findLatestUpdatedAtByLibraryId(libraryId);
        Optional<Instant> latestUploadTask = uploadTaskRepository.findLatestUpdatedAtByLibraryId(libraryId);

        long photoFeedVersion = maxOf(
                mediaRepository.findLatestUpdatedAtByLibraryIdAndDeletedAtIsNull(libraryId),
                mediaRepository.findLatestUpdatedAtByLibraryId(libraryId)
        );

        long albumsVersion = maxOf(
                albumRepository.findLatestUpdatedAtByLibraryIdAndDeletedAtIsNullAndSystemKeyIsNull(libraryId),
                albumRepository.findLatestUpdatedAtByLibraryIdAndSystemKeyIsNull(libraryId),
                postRepository.findLatestUpdatedAtByLibraryIdAndDeletedAtIsNullAndSystemKeyIsNull(libraryId),
                postRepository.findLatestUpdatedAtByLibraryIdAndSystemKeyIsNull(libraryId),
                postMediaRepository.findLatestUpdatedAtByLibraryId(libraryId),
                latestComment
        );

        long trashVersion = toEpochMillis(
                trashItemRepository.findLatestUpdatedAtByLibraryId(libraryId)
        );

        long lifeConsoleVersion = maxOf(
                bowelEventRepository.findLatestUpdatedAtByLibraryId(libraryId),
                postRepository.findLatestUpdatedAtByLibraryIdAndDomainAndDeletedAtIsNullAndSystemKeyIsNotNull(libraryId, "life"),
                ledgerSnapshotRepository.findLatestUpdatedAtByLibraryId(libraryId)
        );

        long chatVersion = toEpochMillis(
                importedChatRepository.findLatestUpdatedAtByLibraryId(libraryId)
        );

        long notificationVersion = maxOf(
                Optional.of(Instant.ofEpochMilli(photoFeedVersion)),
                Optional.of(Instant.ofEpochMilli(albumsVersion)),
                Optional.of(Instant.ofEpochMilli(trashVersion)),
                Optional.of(Instant.ofEpochMilli(lifeConsoleVersion)),
                Optional.of(Instant.ofEpochMilli(chatVersion)),
                latestComment,
                latestUploadTask
        );

        return new SyncVersionsResponse(
                photoFeedVersion,
                albumsVersion,
                trashVersion,
                notificationVersion,
                lifeConsoleVersion,
                chatVersion,
                System.currentTimeMillis()
        );
    }

    @SafeVarargs
    private static long maxOf(Optional<Instant>... optionals) {
        long max = 0L;
        for (Optional<Instant> opt : optionals) {
            if (opt.isPresent() && opt.get() != null) {
                max = Math.max(max, opt.get().toEpochMilli());
            }
        }
        return max;
    }

    private static long toEpochMillis(Optional<Instant> instant) {
        return instant.map(Instant::toEpochMilli).orElse(0L);
    }
}
