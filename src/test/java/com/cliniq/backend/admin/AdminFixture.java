package com.cliniq.backend.admin;

import com.cliniq.backend.enums.*;
import com.cliniq.backend.model.*;
import com.cliniq.backend.repository.*;
import com.cliniq.backend.service.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminFixture {
    final ScheduleRepository schedules = mock(ScheduleRepository.class);
    final AppointmentRepository appointments = mock(AppointmentRepository.class);
    final QueueRepository queues = mock(QueueRepository.class);
    final StaffRepository staff = mock(StaffRepository.class);
    final NotificationRepository notifications = mock(NotificationRepository.class);
    final AuditLogsService audit = mock(AuditLogsService.class);
    final AccessPolicy policy = new AccessPolicy(staff);
    final Clock clock = Clock.fixed(Instant.parse("2030-01-01T07:00:00Z"), ZoneId.of("Africa/Gaborone"));
    final UUID userId = UUID.randomUUID();
    final Jwt jwt = Jwt.withTokenValue("test").header("alg", "test").subject(userId.toString()).build();
    final Department department = new Department();
    final Staff doctor = profile(StaffRole.DOCTOR, true);
    final Schedule slot = new Schedule();

    AdminFixture() {
        department.setDepartmentId(UUID.randomUUID());
        department.setName("General care");
        doctor.setDepartment(department);
        slot.setSlotId(UUID.randomUUID());
        slot.setSlotDate(LocalDate.of(2030, 1, 1));
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(11, 0));
        slot.setSlotStatus(SlotStatus.OPEN);
        slot.setCapacity(3);
        slot.setStaff(doctor);
        when(staff.findById(doctor.getStaffId())).thenReturn(Optional.of(doctor));
        when(schedules.findByIdForUpdate(slot.getSlotId())).thenReturn(Optional.of(slot));
        when(schedules.save(any())).thenAnswer(call -> {
            Schedule saved = call.getArgument(0);
            if (saved.getSlotId() == null) saved.setSlotId(UUID.randomUUID());
            return saved;
        });
    }

    Staff profile(StaffRole role, boolean active) {
        Staff profile = new Staff();
        profile.setStaffId(UUID.randomUUID());
        profile.setAuthUserId(userId);
        profile.setFirstName("Ada");
        profile.setLastName("Doctor");
        profile.setStaffRole(role);
        profile.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
        profile.setActive(active);
        return profile;
    }

    void actor(Staff profile) { when(staff.findByAuthUserId(userId)).thenReturn(Optional.ofNullable(profile)); }
    void admin() { actor(profile(StaffRole.ADMIN, true)); }

    Appointment appointment(Patient patient, AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(UUID.randomUUID());
        appointment.setAppointmentStatus(status);
        appointment.setPriority(Priority.NORMAL);
        appointment.setSlot(slot);
        appointment.setStaff(doctor);
        appointment.setPatient(patient);
        appointment.setCreatedAt(LocalDateTime.of(2029, 12, 20, 12, 0));
        return appointment;
    }

    Patient patient() {
        return new Patient(UUID.randomUUID(), UUID.randomUUID(), "Grace", "Patient", "private-id",
                "grace@example.com", "71234567");
    }

    void assertStatus(HttpStatus expected, Runnable action) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, action::run).getStatusCode());
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(T service, PlatformTransactionManager transactions) {
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }
}
