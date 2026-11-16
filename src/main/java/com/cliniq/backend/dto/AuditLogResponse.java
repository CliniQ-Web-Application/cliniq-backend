package com.cliniq.backend.dto;

import com.cliniq.backend.model.AuditLogs;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditLogResponse(
        UUID logId,
        String action,
        String reason,
        String entityName,
        String entityId,
        LocalDateTime createdAt,
        Actor staff
) {
    public record Actor(String firstName, String lastName) {}

    public static AuditLogResponse from(AuditLogs entry) {
        if (entry == null) return null;
        Actor actor = entry.getStaff() == null ? null
                : new Actor(entry.getStaff().getFirstName(), entry.getStaff().getLastName());
        return new AuditLogResponse(
                entry.getLogId(),
                entry.getAction(),
                entry.getReason(),
                entry.getEntityName(),
                entry.getEntityId(),
                entry.getCreatedAt(),
                actor
        );
    }
}
