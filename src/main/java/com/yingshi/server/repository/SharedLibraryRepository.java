package com.yingshi.server.repository;

import com.yingshi.server.domain.SharedLibraryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedLibraryRepository extends JpaRepository<SharedLibraryEntity, String> {
}
