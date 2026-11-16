package com.cliniq.backend.repository;

import com.cliniq.backend.model.Appointment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    @Query("""
            select distinct a from Appointment a
            join fetch a.staff st
            left join fetch st.department
            left join fetch a.patient
            left join fetch a.slot
            """)
    List<Appointment> findAllForAnalytics();

    boolean existsBySlot_SlotId(UUID slotId);

    @Query("""
            select count(a) from Appointment a
            where a.slot.slotId = :slotId
              and a.appointmentStatus <> com.cliniq.backend.enums.AppointmentStatus.CANCELLED
            """)
    long countOccupyingBySlotId(@Param("slotId") UUID slotId);

    // Scalar lookup avoids caching a stale appointment before waiting for the slot lock.
    @Query("select a.slot.slotId from Appointment a where a.appointmentId = :id")
    Optional<UUID> findSlotIdByAppointmentId(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.appointmentId = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select a.slot.slotId, count(a) from Appointment a
            where a.slot.slotId in :ids
              and a.appointmentStatus <> com.cliniq.backend.enums.AppointmentStatus.CANCELLED
            group by a.slot.slotId
            """)
    List<Object[]> countOccupyingBySlotIds(@Param("ids") Collection<UUID> ids);

    @Query("""
            select a from Appointment a join fetch a.slot s
            join fetch a.staff st left join fetch st.department
            where a.patient.patientID = :patientId
            order by coalesce(a.bookedAt, a.createdAt) desc nulls last, a.appointmentId desc
            """)
    List<Appointment> findBookingsByPatientId(@Param("patientId") UUID patientId);

    @Query("""
            select a from Appointment a join fetch a.slot s
            join fetch a.staff st left join fetch st.department
            where a.appointmentId = :id and a.patient.patientID = :patientId
            """)
    Optional<Appointment> findBookingByIdAndPatientId(@Param("id") UUID id, @Param("patientId") UUID patientId);
}
