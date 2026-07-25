package com.yingshi.server.repository;

import com.yingshi.server.domain.AppReleaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppReleaseRepository extends JpaRepository<AppReleaseEntity, String> {

    /**
     * 获取指定平台已发布的最新版本（按创建时间倒序）。
     */
    @Query("""
            SELECT r FROM AppReleaseEntity r
            WHERE r.platform = :platform
              AND r.published = true
            ORDER BY r.createdAt DESC
            LIMIT 1
            """)
    Optional<AppReleaseEntity> findLatestPublished(@Param("platform") String platform);
}
