package com.cliniq.backend.service;

import com.cliniq.backend.dto.AuditLogResponse;
import com.cliniq.backend.model.AuditLogs;
import com.cliniq.backend.model.Staff;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.cliniq.backend.repository.AuditLogsRepository;
import com.cliniq.backend.repository.StaffRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuditLogsService {
    private final AuditLogsRepository auditLogsRepository;
    private final StaffRepository staffRepository;

    public AuditLogsService(AuditLogsRepository auditLogsRepository, StaffRepository staffRepository) {
        this.auditLogsRepository = auditLogsRepository;
        this.staffRepository = staffRepository;
    }

    public List<AuditLogs> getAllAuditLogs() {
        return auditLogsRepository.findAllNewestFirst();
    }

    public AuditLogs getAuditLogById(UUID id) {
        return auditLogsRepository.findById(id).orElseThrow(() -> new RuntimeException("AuditLog not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogs(Pageable pageable) {
        return auditLogsRepository.findAllForList(pageable).map(AuditLogResponse::from);
    }

    @Transactional(readOnly = true)
    public AuditLogResponse getAuditLogDto(UUID id) {
        return AuditLogResponse.from(getAuditLogById(id));
    }

    @Transactional
    public AuditLogs record(String action, String reason, String entityName, Object entityId, Jwt jwt) {
        Staff actor = null;
        if (jwt != null && jwt.getSubject() != null) {
            try {
                actor = staffRepository.findByAuthUserId(UUID.fromString(jwt.getSubject())).orElse(null);
            } catch (IllegalArgumentException ignored) {
                // System or malformed tokens are still recorded without a staff relation.
            }
        }
        AuditLogs entry = new AuditLogs();
        entry.setAction(action);
        entry.setReason(reason);
        entry.setEntityName(entityName);
        entry.setEntityId(String.valueOf(entityId));
        entry.setCreatedAt(LocalDateTime.now());
        entry.setStaff(actor);
        return auditLogsRepository.save(entry);
    }

}