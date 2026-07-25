package com.yingshi.server.service.sync;

import com.yingshi.server.repository.AlbumRepository;
import com.yingshi.server.repository.BowelEventRepository;
import com.yingshi.server.repository.CommentRepository;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.repository.TrashItemRepository;
import com.yingshi.server.repository.UploadTaskRepository;
import com.yingshi.server.repository.chat.ImportedChatRepository;
import com.yingshi.server.repository.ledger.LedgerAccountRepository;
import com.yingshi.server.repository.ledger.LedgerBookRepository;
import com.yingshi.server.repository.ledger.LedgerBudgetRepository;
import com.yingshi.server.repository.ledger.LedgerCategoryBudgetRepository;
import com.yingshi.server.repository.ledger.LedgerCategoryRepository;
import com.yingshi.server.repository.ledger.LedgerDeletedItemRepository;
import com.yingshi.server.repository.ledger.LedgerRecurringOccurrenceRepository;
import com.yingshi.server.repository.ledger.LedgerRecurringRuleRepository;
import com.yingshi.server.repository.ledger.LedgerTransactionRepository;
import com.yingshi.server.dto.sync.SyncVersionsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final MediaRepository mediaRepository;
    private final PostRepository postRepository;
    private final AlbumRepository albumRepository;
    private final TrashItemRepository trashItemRepository;
    private final BowelEventRepository bowelEventRepository;
    private final PostMediaRepository postMediaRepository;
    // Round 3 FR-5: lifeConsoleVersion 改为读取 9 张关系表 MAX(updated_at)，移除 LedgerSnapshotRepository 依赖
    private final LedgerBookRepository ledgerBookRepository;
    private final LedgerCategoryRepository ledgerCategoryRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerBudgetRepository ledgerBudgetRepository;
    private final LedgerCategoryBudgetRepository ledgerCategoryBudgetRepository;
    private final LedgerDeletedItemRepository ledgerDeletedItemRepository;
    private final LedgerRecurringRuleRepository ledgerRecurringRuleRepository;
    private final LedgerRecurringOccurrenceRepository ledgerRecurringOccurrenceRepository;
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
            LedgerBookRepository ledgerBookRepository,
            LedgerCategoryRepository ledgerCategoryRepository,
            LedgerAccountRepository ledgerAccountRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerBudgetRepository ledgerBudgetRepository,
            LedgerCategoryBudgetRepository ledgerCategoryBudgetRepository,
            LedgerDeletedItemRepository ledgerDeletedItemRepository,
            LedgerRecurringRuleRepository ledgerRecurringRuleRepository,
            LedgerRecurringOccurrenceRepository ledgerRecurringOccurrenceRepository,
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
        this.ledgerBookRepository = ledgerBookRepository;
        this.ledgerCategoryRepository = ledgerCategoryRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerBudgetRepository = ledgerBudgetRepository;
        this.ledgerCategoryBudgetRepository = ledgerCategoryBudgetRepository;
        this.ledgerDeletedItemRepository = ledgerDeletedItemRepository;
        this.ledgerRecurringRuleRepository = ledgerRecurringRuleRepository;
        this.ledgerRecurringOccurrenceRepository = ledgerRecurringOccurrenceRepository;
        this.commentRepository = commentRepository;
        this.uploadTaskRepository = uploadTaskRepository;
        this.importedChatRepository = importedChatRepository;
    }

    @Transactional(readOnly = true)
    public SyncVersionsResponse getVersions(String libraryId) {
        Optional<Instant> latestComment = commentRepository.findLatestUpdatedAtByLibraryId(libraryId);
        // 排除 life domain 的 upload_task，避免 life 上传任务影响 notificationVersion
        // life 上传任务的变化已通过 lifeConsoleVersion 反映，无需再通过 notificationVersion 重复触发
        Optional<Instant> latestUploadTask = uploadTaskRepository.findLatestUpdatedAtByLibraryIdAndDomainNotLife(libraryId);

        long mediaNotLifeActive = toEpochMillis(mediaRepository.findLatestUpdatedAtByLibraryIdAndDeletedAtIsNullAndDomainNotLife(libraryId));
        long mediaNotLifeAll = toEpochMillis(mediaRepository.findLatestUpdatedAtByLibraryIdAndDomainNotLife(libraryId));
        long photoFeedVersion = Math.max(mediaNotLifeActive, mediaNotLifeAll);

        long albumActiveNonSys = toEpochMillis(albumRepository.findLatestUpdatedAtByLibraryIdAndDeletedAtIsNullAndSystemKeyIsNull(libraryId));
        long albumAllNonSys = toEpochMillis(albumRepository.findLatestUpdatedAtByLibraryIdAndSystemKeyIsNull(libraryId));
        long postActiveNonSys = toEpochMillis(postRepository.findLatestUpdatedAtByLibraryIdAndDeletedAtIsNullAndSystemKeyIsNull(libraryId));
        long postAllNonSys = toEpochMillis(postRepository.findLatestUpdatedAtByLibraryIdAndSystemKeyIsNull(libraryId));
        long postMediaExclSys = toEpochMillis(postMediaRepository.findLatestUpdatedAtByLibraryIdExcludingSystemAlbums(libraryId));
        long commentMs = toEpochMillis(latestComment);
        long albumsVersion = maxOf(
                Optional.ofNullable(albumActiveNonSys > 0 ? Instant.ofEpochMilli(albumActiveNonSys) : null),
                Optional.ofNullable(albumAllNonSys > 0 ? Instant.ofEpochMilli(albumAllNonSys) : null),
                Optional.ofNullable(postActiveNonSys > 0 ? Instant.ofEpochMilli(postActiveNonSys) : null),
                Optional.ofNullable(postAllNonSys > 0 ? Instant.ofEpochMilli(postAllNonSys) : null),
                Optional.ofNullable(postMediaExclSys > 0 ? Instant.ofEpochMilli(postMediaExclSys) : null),
                latestComment
        );

        // P1-3 根因修复: trashVersion 只包含 photo 回收站 (lifeCategory IS NULL),
        // 避免 life 回收站操作 (删除人物/吃饭/大便媒体) 让 trashVersion 上涨 → notificationVersion 上涨
        // → notificationsStale=true → staleState 重建 → 照片流页面 collectAsState 重组 (用户感知为"照片流被刷新")
        long trashVersion = toEpochMillis(
                trashItemRepository.findLatestUpdatedAtByLibraryIdAndLifeCategoryIsNull(libraryId)
        );

        long lifeConsoleVersion = maxOf(
                bowelEventRepository.findLatestUpdatedAtByLibraryId(libraryId),
                // P1-1 改造: 直接查 media.life domain 更新时间，不再依赖 post.system_key IS NOT NULL（life 已不再创建 post）
                mediaRepository.findLatestUpdatedAtByLibraryIdAndDomainLife(libraryId),
                mediaRepository.findLatestUpdatedAtByLibraryIdAndDeletedAtIsNullAndDomainLife(libraryId),
                // Round 3 FR-5: lifeConsoleVersion 改为读取 9 张账本关系表的 MAX(updated_at)，
                // 不再依赖已废弃的 ledger_snapshots 表。关系表的增删改（含软删除）现在能
                // 正确推动 lifeConsoleVersion 上涨，客户端 lifeConsoleStale 可正确触发刷新。
                ledgerBookRepository.findLatestUpdatedAtByLibraryId(libraryId),
                ledgerCategoryRepository.findLatestUpdatedAtByLibraryId(libraryId),
                ledgerAccountRepository.findLatestUpdatedAtByLibraryId(libraryId),
                ledgerTransactionRepository.findLatestUpdatedAtByLibraryId(libraryId),
                ledgerBudgetRepository.findLatestUpdatedAtByLibraryId(libraryId),
                ledgerCategoryBudgetRepository.findLatestUpdatedAtByLibraryId(libraryId),
                ledgerDeletedItemRepository.findLatestUpdatedAtByLibraryId(libraryId),
                ledgerRecurringRuleRepository.findLatestUpdatedAtByLibraryId(libraryId),
                ledgerRecurringOccurrenceRepository.findLatestUpdatedAtByLibraryId(libraryId),
                // P1-3 根因修复: life 回收站版本纳入 lifeConsoleVersion (而非 trashVersion),
                // 确保 life 回收站操作的版本变化由 lifeConsoleStale 追踪, 不影响 photo 模块
                trashItemRepository.findLatestUpdatedAtByLibraryIdAndLifeCategoryIsNotNull(libraryId)
        );

        long chatVersion = toEpochMillis(
                importedChatRepository.findLatestUpdatedAtByLibraryId(libraryId)
        );

        // P1-3 根因修复: notificationVersion 不再包含 lifeConsoleVersion。
        // 之前 life 操作(大便/位置更新/人物/吃饭上传)会让 notificationVersion 涨,
        // 客户端 notificationsStale=true → maybeShowFallbackNotification 反复查询通知中心 API,
        // 导致:
        //   1. 通知重复推送(SSE 推一条 + 轮询回退又一条, 固定 ID 导致替换)
        //   2. 照片流被 life 操作间接触发刷新(notificationsStale 变化触发 staleState 更新)
        // life 通知现在完全由 SSE 推送负责, 不再依赖 notificationVersion 触发轮询回退。
        // life 数据刷新由 lifeConsoleVersion 独立追踪, 不受影响。
        long notificationVersion = maxOf(
                Optional.of(Instant.ofEpochMilli(photoFeedVersion)),
                Optional.of(Instant.ofEpochMilli(albumsVersion)),
                Optional.of(Instant.ofEpochMilli(trashVersion)),
                Optional.of(Instant.ofEpochMilli(chatVersion)),
                latestComment,
                latestUploadTask
        );

        // 诊断日志：打印 photoFeedVersion 和 albumsVersion 的各组成部分，便于排查 life 操作影响照片流刷新的问题
        log.warn("getVersions: libraryId={} photoFeedVersion={} (mediaNotLifeActive={} mediaNotLifeAll={}) albumsVersion={} (albumActiveNonSys={} albumAllNonSys={} postActiveNonSys={} postAllNonSys={} postMediaExclSys={} commentMs={}) trashVersion={} lifeConsoleVersion={} chatVersion={} notificationVersion={} latestUploadTask={}",
                libraryId, photoFeedVersion, mediaNotLifeActive, mediaNotLifeAll,
                albumsVersion, albumActiveNonSys, albumAllNonSys, postActiveNonSys, postAllNonSys, postMediaExclSys, commentMs,
                trashVersion, lifeConsoleVersion, chatVersion, notificationVersion, toEpochMillis(latestUploadTask));

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
