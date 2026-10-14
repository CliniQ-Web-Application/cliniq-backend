package com.cliniq.backend.service;

import com.cliniq.backend.model.AuditLogs;
import com.cliniq.backend.repository.AuditLogsRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AuditLogsService {
    private final AuditLogsRepository auditLogsRepository;

    public AuditLogsService(AuditLogsRepository auditLogsRepository) {
        this.auditLogsRepository = auditLogsRepository;
    }

    public List<AuditLogs> getAllAuditLogs() {
        return auditLogsRepository.findAll();
    }

    public AuditLogs getAuditLogById(UUID id) {
        return auditLogsRepository.findById(id).orElseThrow(() -> new RuntimeException("AuditLog not found with id: " + id));
    }

    public AuditLogs createAuditLog(AuditLogs auditLog) {
        return auditLogsRepository.save(auditLog);
    }

    public AuditLogs updateAuditLog(UUID id, AuditLogs details) {
        AuditLogs auditLog = getAuditLogById(id);
        auditLog.setAction(details.getAction());
        auditLog.setReason(details.getReason());
        auditLog.setEntityName(details.getEntityName());
        auditLog.setEntityId(details.getEntityId());
        auditLog.setCreatedAt(details.getCreatedAt());
        auditLog.setStaff(details.getStaff());
        return auditLogsRepository.save(auditLog);
    }

    public void deleteAuditLog(UUID id) {
        if (!auditLogsRepository.existsById(id)) {
            throw new RuntimeException("AuditLog not found with id: " + id);
        }
        auditLogsRepository.deleteById(id);
    }
}