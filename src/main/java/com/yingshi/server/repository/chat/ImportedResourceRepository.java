package com.yingshi.server.repository.chat;

import com.yingshi.server.domain.chat.ImportedResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImportedResourceRepository extends JpaRepository<ImportedResourceEntity, Long> {

    List<ImportedResourceEntity> findByLibraryId(String libraryId);

    List<ImportedResourceEntity> findByLibraryIdAndIdIn(String libraryId, Collection<Long> ids);

    List<ImportedResourceEntity> findByLibraryIdAndUpdatedAtAfter(String libraryId, Instant since);

    void deleteByLibraryIdAndIdIn(String libraryId, Collection<Long> ids);

    Optional<ImportedResourceEntity> findByIdAndLibraryId(Long id, String libraryId);

    List<ImportedResourceEntity> findByLibraryIdAndMessageId(String libraryId, Long messageId);

    Optional<ImportedResourceEntity> findFirstByLibraryIdAndMd5AndStoredObjectKeyIsNotNull(String libraryId, String md5);
}
