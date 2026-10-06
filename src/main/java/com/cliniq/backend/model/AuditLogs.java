package com.cliniq.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class AuditLogs {
    @Id
    private int logId;
    private String action;
    private String reason;
    private String entityName;
    private int entityId;
    private LocalDateTime createdAt;
    private int staffId;
}
