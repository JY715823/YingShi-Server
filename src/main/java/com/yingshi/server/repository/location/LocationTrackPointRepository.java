package com.yingshi.server.repository.location;

import com.yingshi.server.domain.location.LocationTrackPointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface LocationTrackPointRepository extends JpaRepository<LocationTrackPointEntity, Long> {

    /** 拉取 library 内（两人）since 之后的轨迹点，足迹地图用。 */
    List<LocationTrackPointEntity> findByLibraryIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(
            String libraryId, Instant since);

    /** 重传幂等判断：同一用户同一采样时刻视为同一点。 */
    boolean existsByUserIdAndRecordedAt(String userId, Instant recordedAt);

    /** 查指定时刻附近的点（用于服务端诊断/未来的推断回填），±window 范围内按时间接近排序。 */
    @Query("""
            SELECT p FROM LocationTrackPointEntity p
            WHERE p.userId = :userId
              AND p.recordedAt BETWEEN :start AND :end
            ORDER BY p.recordedAt ASC
            """)
    List<LocationTrackPointEntity> findAround(
            @Param("userId") String userId,
            @Param("start") Instant start,
            @Param("end") Instant end);
}
