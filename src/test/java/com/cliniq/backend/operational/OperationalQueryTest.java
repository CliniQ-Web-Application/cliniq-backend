package com.cliniq.backend.operational;

import com.cliniq.backend.model.*;
import com.cliniq.backend.model.Queue;
import com.cliniq.backend.repository.AppointmentRepository;
import com.cliniq.backend.repository.AuditLogsRepository;
import com.cliniq.backend.enums.SlotStatus;
import com.cliniq.backend.repository.QueueRepository;
import com.cliniq.backend.repository.ScheduleRepository;
import jakarta.persistence.LockModeType;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Validate HQL against the project's real Hibernate metadata without opening a database connection. */
class OperationalQueryTest {
    @Test
    void repositoryQueriesAreValidForCurrentHibernate() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        try {
            var metadata = new MetadataSources(registry);
            for (Class<?> entity : new Class<?>[]{Appointment.class, Patient.class, Staff.class, Schedule.class,
                    Department.class, Queue.class, Notification.class, AuditLogs.class}) {
                metadata.addAnnotatedClass(entity);
            }
            try (SessionFactory factory = metadata.buildMetadata().buildSessionFactory();
                 var session = factory.openSession()) {
                for (Class<?> repository : new Class<?>[]{AppointmentRepository.class, AuditLogsRepository.class, QueueRepository.class, ScheduleRepository.class}) {
                    Arrays.stream(repository.getDeclaredMethods())
                            .filter(method -> method.isAnnotationPresent(Query.class))
                            .forEach(method -> assertDoesNotThrow(() -> {
                                String hql = method.getAnnotation(Query.class).value();
                                if (method.getReturnType() == Double.class) {
                                    session.createQuery(hql, Double.class);
                                } else {
                                    var query = session.createQuery(hql, Object.class);
                                    if (method.getName().equals("findAdminAppointments")) assertOptionalUuidBindings(query);
                                }
                                String countQuery = method.getAnnotation(Query.class).countQuery();
                                if (!countQuery.isBlank()) {
                                    var query = session.createQuery(countQuery, Long.class);
                                    if (method.getName().equals("findAdminAppointments")) assertOptionalUuidBindings(query);
                                }
                            }, method.getName()));
                }
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private void assertOptionalUuidBindings(org.hibernate.query.Query<?> query) {
        for (String parameter : new String[]{"departmentId", "doctorId"}) {
            assertEquals(UUID.class, query.getParameter(parameter).getParameterType());
            query.setParameter(parameter, null, UUID.class);
            assertNull(query.getParameterValue(parameter));
            UUID id = UUID.randomUUID();
            query.setParameter(parameter, id, UUID.class);
            assertEquals(id, query.getParameterValue(parameter));
        }
    }

    @Test
    void adminOptionalUuidNullChecksAreExplicitlyTypedInBothDataAndCountQueries() {
        var method = Arrays.stream(AppointmentRepository.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("findAdminAppointments")).findFirst().orElseThrow();
        var annotation = method.getAnnotation(Query.class);
        for (String query : new String[]{annotation.value(), annotation.countQuery()}) {
            assertTrue(query.contains("(cast(:departmentId as java.util.UUID) is null or"));
            assertTrue(query.contains("(cast(:doctorId as java.util.UUID) is null or st.staffId = :doctorId)"));
            assertFalse(query.contains("(:departmentId is null"));
            assertFalse(query.contains("(:doctorId is null"));
        }
    }

    @Test
    void adminAppointmentQueryFiltersBeforePaginationWithInclusiveScheduledDateWalkInFallbackAndStableNewestOrder() throws Exception {
        var method = Arrays.stream(AppointmentRepository.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("findAdminAppointments")).findFirst().orElseThrow();
        var annotation = method.getAnnotation(Query.class);
        String query = annotation.value().replaceAll("\\s+", " ").trim();
        assertEquals("coalesce(a.bookedAt, a.createdAt) desc nulls last, a.appointmentId desc", query.split("order by ")[1]);
        assertTrue(query.contains("coalesce(s.slotDate, cast(a.createdAt as LocalDate)) >= :fromDate"));
        assertTrue(query.contains("coalesce(s.slotDate, cast(a.createdAt as LocalDate)) <= :toDate"));
        for (String filter : new String[]{"a.appointmentStatus = :status", "else d.departmentId end = :departmentId", "st.staffId = :doctorId",
                "p.patientName", "p.patientSurname", "p.patientEmail", "p.phone", "cast(a.appointmentId as string)"}) {
            assertTrue(query.contains(filter), filter);
            assertTrue(annotation.countQuery().contains(filter), filter);
        }
        assertTrue(org.springframework.data.domain.Page.class.isAssignableFrom(method.getReturnType()));
        assertTrue(Arrays.asList(method.getParameterTypes()).contains(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void adminDepartmentFiltersAndBatchMappingUseSameSingleQueueWithoutMultiplyingPages() throws Exception {
        var method = Arrays.stream(AppointmentRepository.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("findAdminAppointments")).findFirst().orElseThrow();
        var annotation = method.getAnnotation(Query.class);
        for (String hql : new String[]{annotation.value(), annotation.countQuery()}) {
            assertTrue(hql.contains("case when s.slotId is null and exists (select 1 from Queue q where q.appointment = a)"));
            assertTrue(hql.contains("then (select q.department.departmentId from Queue q where q.appointment = a and"));
            assertTrue(hql.contains("else d.departmentId end = :departmentId"));
            assertTrue(hql.contains(QueueRepository.ADMIN_FIRST_QUEUE));
            assertFalse(hql.substring(0, hql.indexOf("where")).contains("Queue"), "Queue must not join the paged/count root");
        }
        String batch = QueueRepository.class.getMethod("findAdminDepartmentsByAppointmentIds", java.util.Collection.class)
                .getAnnotation(Query.class).value();
        assertTrue(batch.contains("select q.appointment.appointmentId, d from Queue q"));
        assertTrue(batch.contains("left join q.department d"), "A queue with a missing department must not trigger staff fallback");
        assertTrue(batch.contains("q.appointment.appointmentId in :appointmentIds and q.appointment.slot is null"));
        assertTrue(batch.contains(QueueRepository.ADMIN_FIRST_QUEUE));
        assertTrue(QueueRepository.ADMIN_FIRST_QUEUE.contains("earlier.appointment = q.appointment and earlier.queueId < q.queueId"));
    }

    @Test
    void broadcastQueryTargetsOneWindowAndOnlyRequestedStatuses() throws Exception {
        String query = AppointmentRepository.class.getMethod("findBroadcastRecipients", UUID.class, java.util.Collection.class)
                .getAnnotation(Query.class).value();
        assertTrue(query.contains("a.slot.slotId = :slotId and a.appointmentStatus in :statuses"));
        assertTrue(query.contains("join fetch a.patient"));
    }

    @Test
    void patientBookingsUseReservationTimeWithCreationFallbackNullsLastAndStableId() throws Exception {
        String query = AppointmentRepository.class.getMethod("findBookingsByPatientId", UUID.class)
                .getAnnotation(Query.class).value().replaceAll("\\s+", " ").trim();
        assertTrue(query.contains("where a.patient.patientID = :patientId"));
        assertEquals("coalesce(a.bookedAt, a.createdAt) desc nulls last, a.appointmentId desc",
                query.split("order by ")[1]);
    }

    @Test
    void auditLogsUseCreationTimeNullsLastAndStableId() throws Exception {
        String query = AuditLogsRepository.class.getMethod("findAllNewestFirst")
                .getAnnotation(Query.class).value().replaceAll("\\s+", " ").trim();
        assertEquals("a.createdAt desc nulls last, a.logId desc", query.split("order by ")[1]);
    }

    @Test
    void bookingAvailabilityStillUsesSoonestScheduledSlotFirst() throws Exception {
        String query = ScheduleRepository.class.getMethod("findBookingCandidates", SlotStatus.class, LocalDate.class)
                .getAnnotation(Query.class).value().replaceAll("\\s+", " ").trim();
        assertEquals("s.slotDate, s.startTime, s.slotId", query.split("order by ")[1]);
    }

    @Test
    void consultationAverageIsAScalarDatabaseAggregateOfPositiveCompletedDepartmentHistory() throws Exception {
        var method = QueueRepository.class.getMethod("averageCompletedConsultationSeconds", Department.class);
        assertEquals(Double.class, method.getReturnType());
        String query = method.getAnnotation(Query.class).value();
        assertTrue(query.contains("avg(timestampdiff(nanosecond, a.calledTime, a.completedTime)) / 1000000000.0"));
        assertTrue(query.contains("q.department = :department"));
        assertTrue(query.contains("a.appointmentStatus = com.cliniq.backend.enums.AppointmentStatus.COMPLETED"));
        assertTrue(query.contains("a.calledTime is not null"));
        assertTrue(query.contains("a.completedTime is not null"));
        assertTrue(query.contains("a.completedTime > a.calledTime"));
        assertFalse(query.toLowerCase().contains("group by"), "One scalar mean, not one row per consultation");
    }

    @Test
    void bothOccupancyQueriesExcludeOnlyCancelledAndHistoryGuardRemainsUnfiltered() throws Exception {
        for (String methodName : new String[]{"countOccupyingBySlotId", "countOccupyingBySlotIds"}) {
            var method = Arrays.stream(AppointmentRepository.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
            String query = method.getAnnotation(Query.class).value();
            assertTrue(query.contains("a.appointmentStatus <> com.cliniq.backend.enums.AppointmentStatus.CANCELLED"));
            assertFalse(query.contains("AppointmentStatus.BOOKED"), "All non-cancelled history must occupy capacity");
        }
        assertNull(AppointmentRepository.class.getMethod("existsBySlot_SlotId", UUID.class).getAnnotation(Query.class));
        assertEquals(LockModeType.PESSIMISTIC_WRITE, AppointmentRepository.class.getMethod("findByIdForUpdate", UUID.class)
                .getAnnotation(Lock.class).value());
    }
}
