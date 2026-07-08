package com.yingshi.server.repository.chat;

import com.yingshi.server.domain.chat.ImportedMessageSearchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImportedMessageSearchRepository extends JpaRepository<ImportedMessageSearchEntity, Long> {

    List<ImportedMessageSearchEntity> findByLibraryId(String libraryId);

    List<ImportedMessageSearchEntity> findByLibraryIdAndIdIn(String libraryId, Collection<Long> ids);

    List<ImportedMessageSearchEntity> findByLibraryIdAndUpdatedAtAfter(String libraryId, Instant since);

    void deleteByLibraryIdAndIdIn(String libraryId, Collection<Long> ids);

    Optional<ImportedMessageSearchEntity> findByIdAndLibraryId(Long id, String libraryId);
}
