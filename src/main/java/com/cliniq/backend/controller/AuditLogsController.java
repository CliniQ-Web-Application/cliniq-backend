package com.cliniq.backend.controller;

import com.cliniq.backend.dto.AuditLogResponse;
import com.cliniq.backend.dto.PageResponse;
import com.cliniq.backend.service.AuditLogsService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogsController {
    private final AuditLogsService auditLogsService;

    public AuditLogsController(AuditLogsService auditLogsService) {
        this.auditLogsService = auditLogsService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AuditLogResponse>> getAll(
            @PageableDefault(size = 200, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(auditLogsService.getAuditLogs(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(auditLogsService.getAuditLogDto(id));
    }
}
