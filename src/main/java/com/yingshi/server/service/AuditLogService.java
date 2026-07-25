package com.yingshi.server.service;

import com.yingshi.server.domain.AuditLogEntity;
import com.yingshi.server.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for recording audit log entries for critical operations.
 *
 * <p>Records: LOGIN, LOGOUT, UPLOAD, DELETE, IMPORT, PASSWORD_CHANGE, etc.
 * Does NOT log sensitive data (passwords, tokens, full request/response bodies).
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Record an audit log entry.
     *
     * @param actorUserId  who performed the action
     * @param libraryId    scope of the action
     * @param action       action type (LOGIN, UPLOAD, DELETE, etc.)
     * @param resourceType type of resource affected (media, album, etc.), nullable
     * @param resourceId   ID of the resource, nullable
     * @param details      non-sensitive details, nullable
     */
    public void record(String actorUserId, String libraryId, String action,
                       String resourceType, String resourceId, String details) {
        AuditLogEntity entry = new AuditLogEntity();
        entry.setActorUserId(actorUserId);
        entry.setLibraryId(libraryId);
        entry.setAction(action);
        entry.setResourceType(resourceType);
        entry.setResourceId(resourceId);
        entry.setDetails(details);
        auditLogRepository.save(entry);
        log.debug("Audit: actor={} action={} resource={}/{}", actorUserId, action, resourceType, resourceId);
    }
}
