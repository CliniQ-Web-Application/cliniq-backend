package com.cliniq.backend.repository;

import com.cliniq.backend.model.Queue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Department;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

@Repository
public interface QueueRepository extends JpaRepository<Queue, UUID> {
    Optional<Queue> findByAppointment(Appointment appointment);

    @Query(value = """
            select distinct q from Queue q
            join fetch q.appointment a left join fetch a.patient
            left join fetch a.staff st left join fetch st.department
            left join fetch a.slot left join fetch q.department
            """, countQuery = "select count(q) from Queue q")
    Page<Queue> findAllForList(Pageable pageable);

    @Query("""
            select count(q) from Queue q
            where q.department = :department
              and q.appointment.appointmentStatus = :status
            """)
    long countByDepartmentAndAppointmentStatus(@Param("department") Department department,
                                               @Param("status") AppointmentStatus status);

    @Query("""
            select q from Queue q
            join fetch q.appointment a
            where q.department = :department
              and a.appointmentStatus = :status
            """)
    List<Queue> findByDepartmentAndAppointmentStatus(@Param("department") Department department,
                                                      @Param("status") AppointmentStatus status);
}