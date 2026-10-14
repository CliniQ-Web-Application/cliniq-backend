package com.cliniq.backend.model;

import jakarta.persistence.*;
import org.hibernate.grammars.hql.HqlParser;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "auditLogs")
public class AuditLogs {

    public AuditLogs() {}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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

    public UUID getLogId() { return logId; }
    public void setLogId(UUID logId) { this.logId = logId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }
    public int getEntityId() { return entityId; }
    public void setEntityId(int entityId) { this.entityId = entityId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Staff getStaff() { return staff; }
    public void setStaff(Staff staff) { this.staff = staff; }
}