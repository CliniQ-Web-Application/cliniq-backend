package com.cliniq.backend.repository;

import com.cliniq.backend.enums.SlotStatus;
import com.cliniq.backend.model.Schedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {
    @Query("select s from Schedule s left join fetch s.staff st left join fetch st.department order by s.slotDate, s.startTime, s.slotId")
    List<Schedule> findAllForAdmin();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Schedule s where s.slotId = :id")
    Optional<Schedule> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select s from Schedule s
            join fetch s.staff st join fetch st.department
            where s.slotStatus = :status and s.slotDate >= :today
            order by s.slotDate, s.startTime, s.slotId
            """)
    List<Schedule> findBookingCandidates(@Param("status") SlotStatus status, @Param("today") LocalDate today);

    @Query(value = "select s from Schedule s join fetch s.staff st left join fetch st.department",
           countQuery = "select count(s) from Schedule s")
    Page<Schedule> findAllForList(Pageable pageable);

    /** Counts other windows for the same doctor/date that overlap [start, end). */
    @Query("""
            select count(s) from Schedule s
            where s.staff.staffId = :staffId
              and s.slotDate = :slotDate
              and s.startTime < :endTime and s.endTime > :startTime
              and (cast(:excludeId as java.util.UUID) is null or s.slotId <> :excludeId)
            """)
    long countOverlapping(@Param("staffId") UUID staffId,
                          @Param("slotDate") LocalDate slotDate,
                          @Param("startTime") LocalTime startTime,
                          @Param("endTime") LocalTime endTime,
                          @Param("excludeId") UUID excludeId);
}
