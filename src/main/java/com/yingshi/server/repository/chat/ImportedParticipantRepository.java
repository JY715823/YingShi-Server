package com.yingshi.server.repository.chat;

import com.yingshi.server.domain.chat.ImportedParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImportedParticipantRepository extends JpaRepository<ImportedParticipantEntity, Long> {

    List<ImportedParticipantEntity> findByLibraryId(String libraryId);

    List<ImportedParticipantEntity> findByLibraryIdAndIdIn(String libraryId, Collection<Long> ids);

    List<ImportedParticipantEntity> findByLibraryIdAndUpdatedAtAfter(String libraryId, Instant since);

    void deleteByLibraryIdAndIdIn(String libraryId, Collection<Long> ids);

    Optional<ImportedParticipantEntity> findByIdAndLibraryId(Long id, String libraryId);

    Optional<ImportedParticipantEntity> findByLibraryIdAndParticipantStableKey(String libraryId, String participantStableKey);

    List<ImportedParticipantEntity> findByLibraryIdAndChatId(String libraryId, String chatId);
}
