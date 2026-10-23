package com.cliniq.backend.repository;

import com.cliniq.backend.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    boolean existsBySlot_SlotId(UUID slotId);

    @Query("""
            select a from Appointment a join fetch a.slot s
            join fetch a.staff st left join fetch st.department
            where a.patient.patientID = :patientId
            order by s.slotDate, s.startTime, a.appointmentId
            """)
    List<Appointment> findBookingsByPatientId(@Param("patientId") UUID patientId);

    @Query("""
            select a from Appointment a join fetch a.slot s
            join fetch a.staff st left join fetch st.department
            where a.appointmentId = :id and a.patient.patientID = :patientId
            """)
    Optional<Appointment> findBookingByIdAndPatientId(@Param("id") UUID id, @Param("patientId") UUID patientId);
}
