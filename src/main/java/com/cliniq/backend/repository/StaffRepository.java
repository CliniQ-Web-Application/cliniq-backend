package com.cliniq.backend.repository;

import com.cliniq.backend.model.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffRepository extends JpaRepository<Staff, UUID> {
    boolean existsByEmailIgnoreCase(String email);
    Optional<Staff> findByAuthUserId(UUID authUserId);
}
