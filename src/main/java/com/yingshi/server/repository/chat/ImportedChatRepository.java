package com.yingshi.server.repository.chat;

import com.yingshi.server.domain.chat.ImportedChatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImportedChatRepository extends JpaRepository<ImportedChatEntity, String> {

    List<ImportedChatEntity> findByLibraryId(String libraryId);

    List<ImportedChatEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    List<ImportedChatEntity> findByLibraryIdAndUpdatedAtAfter(String libraryId, Instant since);

    void deleteByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    Optional<ImportedChatEntity> findByIdAndLibraryId(String id, String libraryId);

    Optional<ImportedChatEntity> findByLibraryIdAndChatStableKey(String libraryId, String chatStableKey);

    @Query("SELECT MAX(c.updatedAt) FROM ImportedChatEntity c WHERE c.libraryId = :libraryId")
    Optional<Instant> findLatestUpdatedAtByLibraryId(@Param("libraryId") String libraryId);
}
