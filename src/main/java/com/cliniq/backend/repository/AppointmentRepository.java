package com.cliniq.backend.repository;

import com.cliniq.backend.model.Appointment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    /** Shared predicate for every analytics aggregate, pushed down to the database. */
    String ANALYTICS_FILTERS = """
            left join schedule s on s.slot_id = a.slot_id
            left join staff st on st.staff_id = a.staff_id
            where (cast(:from as date) is null or coalesce(s.slot_date, cast(a.created_at as date)) >= cast(:from as date))
              and (cast(:to as date) is null or coalesce(s.slot_date, cast(a.created_at as date)) <= cast(:to as date))
              and (cast(:departmentId as uuid) is null or st.department_id = cast(:departmentId as uuid))
              and (cast(:doctorId as uuid) is null or a.staff_id = cast(:doctorId as uuid))
            """;

    @Query(value = """
            select a.appointment_status as status, count(*) as total
            from appointment a
            """ + ANALYTICS_FILTERS + """
            group by a.appointment_status
            """, nativeQuery = true)
    List<Object[]> countAppointmentsByStatus(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("departmentId") UUID departmentId,
            @Param("doctorId") UUID doctorId);

    @Query(value = """
            select coalesce(round(avg(greatest(0, extract(epoch from (a.called_time - a.check_in_time)) / 60.0))), 0)
            from appointment a
            """ + ANALYTICS_FILTERS + """
              and a.check_in_time is not null and a.called_time is not null
            """, nativeQuery = true)
    Number averageWaitMinutes(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("departmentId") UUID departmentId,
            @Param("doctorId") UUID doctorId);

    @Query(value = """
            select coalesce(d.name, 'Unassigned') as department,
                   round(avg(greatest(0, extract(epoch from (a.called_time - a.check_in_time)) / 60.0))) as average_wait
            from appointment a
            left join schedule s on s.slot_id = a.slot_id
            left join staff st on st.staff_id = a.staff_id
            left join department d on d.department_id = st.department_id
            where (cast(:from as date) is null or coalesce(s.slot_date, cast(a.created_at as date)) >= cast(:from as date))
              and (cast(:to as date) is null or coalesce(s.slot_date, cast(a.created_at as date)) <= cast(:to as date))
              and (cast(:departmentId as uuid) is null or st.department_id = cast(:departmentId as uuid))
              and (cast(:doctorId as uuid) is null or a.staff_id = cast(:doctorId as uuid))
              and a.check_in_time is not null and a.called_time is not null
            group by coalesce(d.name, 'Unassigned')
            """, nativeQuery = true)
    List<Object[]> averageWaitByDepartment(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("departmentId") UUID departmentId,
            @Param("doctorId") UUID doctorId);

    @Query(value = """
            select extract(hour from coalesce(a.check_in_time, a.created_at))::int as hour, count(*) as total
            from appointment a
            """ + ANALYTICS_FILTERS + """
            group by hour order by hour
            """, nativeQuery = true)
    List<Object[]> arrivalsByHour(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("departmentId") UUID departmentId,
            @Param("doctorId") UUID doctorId);

    String ADMIN_FROM = """
            from Appointment a
            left join a.slot s
            left join a.staff st
            left join st.department d
            left join a.patient p
            where (cast(:fromDate as LocalDate) is null or coalesce(s.slotDate, cast(a.createdAt as LocalDate)) >= :fromDate)
              and (cast(:toDate as LocalDate) is null or coalesce(s.slotDate, cast(a.createdAt as LocalDate)) <= :toDate)
              and (:status is null or a.appointmentStatus = :status)
              and (cast(:departmentId as java.util.UUID) is null or
                   case when s.slotId is null and exists (select 1 from Queue q where q.appointment = a)
                        then (select q.department.departmentId from Queue q where q.appointment = a and
            """ + QueueRepository.ADMIN_FIRST_QUEUE + """
                             )
                        else d.departmentId end = :departmentId)
              and (cast(:doctorId as java.util.UUID) is null or st.staffId = :doctorId)
              and (:search is null
                or lower(concat(coalesce(p.patientName, ''), ' ', coalesce(p.patientSurname, ''))) like :search escape '!'
                or lower(coalesce(p.patientEmail, '')) like :search escape '!'
                or lower(coalesce(p.phone, '')) like :search escape '!'
                or lower(concat(coalesce(st.firstName, ''), ' ', coalesce(st.lastName, ''))) like :search escape '!'
                or lower(cast(a.appointmentId as string)) like :search escape '!')
            """;

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"slot", "staff", "staff.department", "patient"})
    @Query(value = "select a " + ADMIN_FROM + " order by coalesce(a.bookedAt, a.createdAt) desc nulls last, a.appointmentId desc",
            countQuery = "select count(a) " + ADMIN_FROM)
    org.springframework.data.domain.Page<Appointment> findAdminAppointments(
            @Param("fromDate") java.time.LocalDate from, @Param("toDate") java.time.LocalDate to,
            @Param("status") com.cliniq.backend.enums.AppointmentStatus status,
            @Param("departmentId") UUID departmentId, @Param("doctorId") UUID doctorId,
            @Param("search") String search, org.springframework.data.domain.Pageable pageable);

    @Query("""
            select a from Appointment a join fetch a.patient
            where a.slot.slotId = :slotId and a.appointmentStatus in :statuses
            order by a.appointmentId
            """)
    List<Appointment> findBroadcastRecipients(@Param("slotId") UUID slotId,
            @Param("statuses") Collection<com.cliniq.backend.enums.AppointmentStatus> statuses);
    @Query("""
            select distinct a from Appointment a
            join fetch a.staff st
            left join fetch st.department
            left join fetch a.patient
            left join fetch a.slot
            """)
    List<Appointment> findAllForAnalytics();

    @Query(value = """
            select distinct a from Appointment a
            join fetch a.staff st left join fetch st.department
            left join fetch a.patient left join fetch a.slot
            """, countQuery = "select count(a) from Appointment a")
    Page<Appointment> findAllForList(Pageable pageable);

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
