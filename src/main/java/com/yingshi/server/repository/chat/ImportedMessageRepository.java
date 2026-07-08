package com.yingshi.server.repository.chat;

import com.yingshi.server.domain.chat.ImportedMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImportedMessageRepository extends JpaRepository<ImportedMessageEntity, Long> {

    List<ImportedMessageEntity> findByLibraryId(String libraryId);

    List<ImportedMessageEntity> findByLibraryIdAndIdIn(String libraryId, Collection<Long> ids);

    List<ImportedMessageEntity> findByLibraryIdAndUpdatedAtAfter(String libraryId, Instant since);

    void deleteByLibraryIdAndIdIn(String libraryId, Collection<Long> ids);

    Optional<ImportedMessageEntity> findByIdAndLibraryId(Long id, String libraryId);

    List<ImportedMessageEntity> findByLibraryIdAndChatId(String libraryId, String chatId);
}
