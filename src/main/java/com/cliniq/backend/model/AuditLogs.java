package com.cliniq.backend.model;

import jakarta.persistence.*;
import org.hibernate.grammars.hql.HqlParser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "auditLogs")
public class AuditLogs {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID logId;

    @Column(nullable = false)
    private String action;

    private String reason;

    @Column(nullable = false)
    private String entityName;

    @Column(nullable = false)
    private int entityId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;
}
