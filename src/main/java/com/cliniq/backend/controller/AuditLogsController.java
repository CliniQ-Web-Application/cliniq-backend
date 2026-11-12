package com.cliniq.backend.controller;

import com.cliniq.backend.model.AuditLogs;
import com.cliniq.backend.service.AuditLogsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogsController {
    private final AuditLogsService auditLogsService;

    public AuditLogsController(AuditLogsService auditLogsService) {
        this.auditLogsService = auditLogsService;
    }

    @GetMapping
    public ResponseEntity<List<AuditLogs>> getAll() {
        return ResponseEntity.ok(auditLogsService.getAllAuditLogs());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogs> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(auditLogsService.getAuditLogById(id));
    }

}