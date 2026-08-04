package com.yingshi.server.service.location;

import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.domain.location.LocationTrackPointEntity;
import com.yingshi.server.dto.location.TrackPointBatchRequest;
import com.yingshi.server.dto.location.TrackPointBatchResponse;
import com.yingshi.server.dto.location.TrackPointDto;
import com.yingshi.server.repository.location.LocationTrackPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * V52: 足迹轨迹点服务。
 *
 * 上行幂等：同一 (userId, recordedAt) 只落一条；下行按 library 范围返回两人轨迹
 * （映世为双人共享空间，足迹地图需展示双方）。
 */
@Service
public class LocationTrackService {

    private static final Logger log = LoggerFactory.getLogger(LocationTrackService.class);

    /** 单次批量上限，防异常客户端打爆。 */
    private static final int MAX_BATCH_SIZE = 500;

    private final LocationTrackPointRepository repository;

    public LocationTrackService(LocationTrackPointRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TrackPointBatchResponse upsertBatch(TrackPointBatchRequest request, AuthenticatedUser currentUser) {
        List<TrackPointBatchRequest.TrackPointItem> points = request.points();
        if (points.size() > MAX_BATCH_SIZE) {
            points = points.subList(0, MAX_BATCH_SIZE);
        }
        int inserted = 0;
        int skipped = 0;
        List<LocationTrackPointEntity> toSave = new ArrayList<>();
        for (TrackPointBatchRequest.TrackPointItem item : points) {
            Instant recordedAt = Instant.ofEpochMilli(item.recordedAtMillis());
            if (repository.existsByUserIdAndRecordedAt(currentUser.userId(), recordedAt)) {
                skipped++;
                continue;
            }
            LocationTrackPointEntity entity = new LocationTrackPointEntity();
            entity.setLibraryId(currentUser.libraryId());
            entity.setUserId(currentUser.userId());
            entity.setLatitude(item.latitude());
            entity.setLongitude(item.longitude());
            entity.setAccuracy(item.accuracy());
            entity.setSource(item.source() == null || item.source().isBlank() ? "alarm" : item.source().trim());
            entity.setRecordedAt(recordedAt);
            toSave.add(entity);
            inserted++;
        }
        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
        }
        log.info("Location track batch: userId={}, received={}, inserted={}, skipped={}",
                currentUser.userId(), points.size(), inserted, skipped);
        return new TrackPointBatchResponse(points.size(), inserted, skipped);
    }

    @Transactional(readOnly = true)
    public List<TrackPointDto> listSince(Long sinceMillis, AuthenticatedUser currentUser) {
        Instant since = sinceMillis == null || sinceMillis <= 0
                ? Instant.EPOCH
                : Instant.ofEpochMilli(sinceMillis);
        return repository
                .findByLibraryIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(currentUser.libraryId(), since)
                .stream()
                .map(entity -> new TrackPointDto(
                        entity.getUserId(),
                        entity.getLatitude(),
                        entity.getLongitude(),
                        entity.getAccuracy(),
                        entity.getSource(),
                        entity.getRecordedAt().toEpochMilli()
                ))
                .toList();
    }
}
