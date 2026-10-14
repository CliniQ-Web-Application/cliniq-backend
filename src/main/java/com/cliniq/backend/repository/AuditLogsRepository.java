package com.cliniq.backend.repository;

import com.cliniq.backend.model.AuditLogs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogsRepository extends JpaRepository<AuditLogs, UUID> { }