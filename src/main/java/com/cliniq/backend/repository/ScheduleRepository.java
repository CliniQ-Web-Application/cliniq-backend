package com.cliniq.backend.repository;

import com.cliniq.backend.enums.SlotStatus;
import com.cliniq.backend.model.Schedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Schedule s where s.slotId = :id")
    Optional<Schedule> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select s from Schedule s
            join fetch s.staff st join fetch st.department
            where s.slotStatus = :status and s.slotDate >= :today
              and not exists (select a.appointmentId from Appointment a where a.slot = s)
            order by s.slotDate, s.startTime, s.slotId
            """)
    List<Schedule> findBookingCandidates(@Param("status") SlotStatus status, @Param("today") LocalDate today);
}
