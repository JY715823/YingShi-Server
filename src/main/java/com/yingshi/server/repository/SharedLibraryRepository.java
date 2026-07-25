package com.yingshi.server.repository;

import com.yingshi.server.domain.SharedLibraryEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SharedLibraryRepository extends JpaRepository<SharedLibraryEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select library from SharedLibraryEntity library where library.id = :id")
    Optional<SharedLibraryEntity> findByIdForUpdate(@Param("id") String id);
}
