package com.cliniq.backend.controller;

import com.cliniq.backend.model.AuditLogs;
import com.cliniq.backend.service.AuditLogsService;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    public ResponseEntity<AuditLogs> create(@RequestBody AuditLogs auditLog) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auditLogsService.createAuditLog(auditLog));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AuditLogs> update(@PathVariable UUID id, @RequestBody AuditLogs auditLog) {
        return ResponseEntity.ok(auditLogsService.updateAuditLog(id, auditLog));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        auditLogsService.deleteAuditLog(id);
        return ResponseEntity.noContent().build();
    }
}