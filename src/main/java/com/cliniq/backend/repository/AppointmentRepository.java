package com.cliniq.backend.repository;

import com.cliniq.backend.model.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
            where (:from is null or coalesce(s.slot_date, cast(a.created_at as date)) >= cast(:from as date))
              and (:to is null or coalesce(s.slot_date, cast(a.created_at as date)) <= cast(:to as date))
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
            where (:from is null or coalesce(s.slot_date, cast(a.created_at as date)) >= cast(:from as date))
              and (:to is null or coalesce(s.slot_date, cast(a.created_at as date)) <= cast(:to as date))
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

    long countBySlot_SlotId(UUID slotId);

    @Query("""
            select a.slot.slotId, count(a) from Appointment a
            where a.slot.slotId in :ids
            group by a.slot.slotId
            """)
    List<Object[]> countBySlotIds(@Param("ids") Collection<UUID> ids);

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
