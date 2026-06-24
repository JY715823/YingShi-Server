package com.yingshi.server.repository;

import com.yingshi.server.domain.PushDeliveryAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PushDeliveryAuditRepository extends JpaRepository<PushDeliveryAuditEntity, String> {

    List<PushDeliveryAuditEntity> findTop30ByLibraryIdOrderByCreatedAtDesc(String libraryId);
}
