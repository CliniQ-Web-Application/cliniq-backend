package com.cliniq.backend.repository;

import com.cliniq.backend.model.AuditLogs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogsRepository extends JpaRepository<AuditLogs, UUID> {
    @Query("""
            select a from AuditLogs a
            order by a.createdAt desc nulls last, a.logId desc
            """)
    List<AuditLogs> findAllNewestFirst();
}