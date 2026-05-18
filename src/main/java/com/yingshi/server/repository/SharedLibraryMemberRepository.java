package com.yingshi.server.repository;

import com.yingshi.server.domain.SharedLibraryMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SharedLibraryMemberRepository extends JpaRepository<SharedLibraryMemberEntity, String> {

    boolean existsByUserIdAndLibraryId(String userId, String libraryId);

    List<SharedLibraryMemberEntity> findByLibraryId(String libraryId);
}
