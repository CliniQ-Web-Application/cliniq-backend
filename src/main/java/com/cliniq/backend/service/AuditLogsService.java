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

}