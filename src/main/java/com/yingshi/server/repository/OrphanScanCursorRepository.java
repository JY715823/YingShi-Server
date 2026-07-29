package com.yingshi.server.repository;

import com.yingshi.server.domain.OrphanScanCursorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrphanScanCursorRepository extends JpaRepository<OrphanScanCursorEntity, String> {
}
