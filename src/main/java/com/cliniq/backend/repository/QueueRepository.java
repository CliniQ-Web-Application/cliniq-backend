package com.cliniq.backend.repository;

import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Department;
import com.cliniq.backend.model.Queue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QueueRepository extends JpaRepository<Queue, UUID> {
    Optional<Queue> findByAppointment(Appointment appointment);

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

    @Query("""
            select q from Queue q
            join fetch q.appointment a
            join fetch a.patient p
            join fetch q.department d
            where d.departmentId = :departmentId
              and a.appointmentStatus = :status
            order by a.createdAt asc, a.appointmentId asc
            """)
    List<Queue> findByDepartmentIdAndAppointmentStatus(@Param("departmentId") UUID departmentId,
                                                       @Param("status") AppointmentStatus status);

    @Query("""
            select q from Queue q
            join fetch q.appointment a
            join fetch a.patient p
            join fetch q.department d
            where d.departmentId = :departmentId
              and a.appointmentStatus in :statuses
            order by q.position asc, a.createdAt asc
            """)
    List<Queue> findActiveByDepartmentId(@Param("departmentId") UUID departmentId,
                                         @Param("statuses") Collection<AppointmentStatus> statuses);
}