package com.cliniq.backend.repository;

import com.cliniq.backend.model.AuditLogs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogsRepository extends JpaRepository<AuditLogs, UUID> {
    @Query(value = "select a from AuditLogs a left join fetch a.staff",
           countQuery = "select count(a) from AuditLogs a")
    Page<AuditLogs> findAllForList(Pageable pageable);
}
