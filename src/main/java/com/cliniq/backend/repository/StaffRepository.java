package com.cliniq.backend.repository;

import com.cliniq.backend.model.Staff;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffRepository extends JpaRepository<Staff, UUID> {
    boolean existsByEmailIgnoreCase(String email);
    Optional<Staff> findByAuthUserId(UUID authUserId);

    @Query(value = "select s from Staff s left join fetch s.department",
           countQuery = "select count(s) from Staff s")
    Page<Staff> findAllForList(Pageable pageable);
}
