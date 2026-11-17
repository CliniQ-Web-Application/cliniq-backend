package com.cliniq.backend.operational;

import com.cliniq.backend.dto.AvailabilityUpdateRequest;
import com.cliniq.backend.dto.PriorityRequest;
import com.cliniq.backend.dto.QueueResponse;
import com.cliniq.backend.dto.WalkInRequest;
import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.enums.AvailabilityStatus;
import com.cliniq.backend.enums.Priority;
import com.cliniq.backend.enums.SlotStatus;
import com.cliniq.backend.enums.StaffRole;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Department;
import com.cliniq.backend.model.Notification;
import com.cliniq.backend.model.Patient;
import com.cliniq.backend.model.Queue;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.AppointmentRepository;
import com.cliniq.backend.repository.DepartmentRepository;
import com.cliniq.backend.repository.PatientRepository;
import com.cliniq.backend.repository.QueueRepository;
import com.cliniq.backend.repository.StaffRepository;
import com.cliniq.backend.repository.ScheduleRepository;
import com.cliniq.backend.service.AccessPolicy;
import com.cliniq.backend.service.AuditLogsService;
import com.cliniq.backend.service.NotificationService;
import com.cliniq.backend.service.OperationalService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OperationalServiceTest {
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final QueueRepository queues = mock(QueueRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final ScheduleRepository schedules = mock(ScheduleRepository.class);
    private final PatientRepository patients = mock(PatientRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final AccessPolicy accessPolicy = mock(AccessPolicy.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final AuditLogsService auditLogsService = mock(AuditLogsService.class);

    private final UUID staffAuthId = UUID.randomUUID();
    private final UUID patientAuthId = UUID.randomUUID();
    private final Department department = department("General care");
    private Staff currentStaff;
    private Patient patient;
    private OperationalService service;

    private Jwt jwt(UUID subject) {
        return Jwt.withTokenValue("test").header("alg", "test").subject(subject.toString()).build();
    }

    private static Department department(String name) {
        Department result = new Department();
        result.setDepartmentId(UUID.randomUUID());
        result.setName(name);
        result.setOperatingHours("08:00 to 16:00");
        return result;
    }

    private static Staff staff(UUID authUserId, boolean active) {
        Staff result = new Staff();
        result.setStaffId(UUID.randomUUID());
        result.setAuthUserId(authUserId);
        result.setFirstName("Ada");
        result.setLastName("Nurse");
        result.setStaffRole(StaffRole.NURSE);
        result.setActive(active);
        result.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        return result;
    }

    private static Patient patient(UUID authUserId) {
        Patient result = new Patient();
        result.setPatientID(UUID.randomUUID());
        result.setAuthUserId(authUserId);
        result.setPatientName("Kabo");
        result.setPatientSurname("Doe");
        result.setPatientEmail("kabo@example.com");
        result.setNationalID(" Omang-123 ".trim());
        result.setPhone("+26770000000");
        return result;
    }

    private static Appointment appointment(AppointmentStatus status, Patient patient, Staff staff) {
        Appointment result = new Appointment();
        result.setAppointmentId(UUID.randomUUID());
        result.setAppointmentStatus(status);
        result.setPriority(Priority.NORMAL);
        result.setPatient(patient);
        result.setStaff(staff);
        result.setCreatedAt(LocalDateTime.of(2030, 1, 1, 8, 0));
        result.setBookedAt(LocalDateTime.of(2030, 1, 1, 8, 30));
        return result;
    }

    private static Queue queue(Appointment appointment, Department department, int position) {
        Queue result = new Queue();
        result.setQueueId(UUID.randomUUID());
        result.setAppointment(appointment);
        result.setDepartment(department);
        result.setPosition(position);
        result.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        result.setTimeStamp(LocalTime.of(9, 0));
        return result;
    }

    @BeforeEach
    void setup() {
        currentStaff = staff(staffAuthId, true);
        currentStaff.setDepartment(department);
        patient = patient(patientAuthId);
        service = new OperationalService(appointments, queues, staff, patients, departments,
                accessPolicy, notificationService, auditLogsService, schedules);
        when(staff.findByAuthUserId(staffAuthId)).thenReturn(Optional.of(currentStaff));
        when(staff.save(any(Staff.class))).thenAnswer(call -> call.getArgument(0));
        when(appointments.save(any(Appointment.class))).thenAnswer(call -> call.getArgument(0));
        when(queues.save(any(Queue.class))).thenAnswer(call -> call.getArgument(0));
        when(queues.countByDepartmentAndAppointmentStatus(any(), any())).thenReturn(0L);
    }

    @Test
    void updatesAvailabilityAndAuditsTheChange() {
        service.updateAvailability(jwt(staffAuthId), new AvailabilityUpdateRequest(AvailabilityStatus.UNAVAILABLE));

        assertEquals(AvailabilityStatus.UNAVAILABLE, currentStaff.getAvailabilityStatus());
        verify(staff).save(currentStaff);
        verify(auditLogsService).record(eq("STAFF_AVAILABILITY_UPDATED"), eq("UNAVAILABLE"),
                eq("Staff"), eq(currentStaff.getStaffId()), any());
    }

    @Test
    void rejectsAvailabilityChangeFromInactiveStaff() {
        currentStaff.setActive(false);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateAvailability(jwt(staffAuthId), new AvailabilityUpdateRequest(AvailabilityStatus.AVAILABLE)));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(staff, never()).save(any());
    }

    @Test
    void walkInCreatesWaitingAppointmentAndQueueEntry() {
        when(patients.findById(patient.getPatientID())).thenReturn(Optional.of(patient));
        when(departments.findById(department.getDepartmentId())).thenReturn(Optional.of(department));
        when(queues.countByDepartmentAndAppointmentStatus(department, AppointmentStatus.WAITING)).thenReturn(4L);

        service.registerWalkIn(jwt(staffAuthId), new WalkInRequest(patient.getPatientID(), department.getDepartmentId()));

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointments).save(appointmentCaptor.capture());
        Appointment created = appointmentCaptor.getValue();
        assertSame(patient, created.getPatient());
        assertSame(currentStaff, created.getStaff());
        assertEquals(AppointmentStatus.WAITING, created.getAppointmentStatus());
        assertEquals(Priority.NORMAL, created.getPriority());
        assertNotNull(created.getCheckInTime());

        ArgumentCaptor<Queue> queueCaptor = ArgumentCaptor.forClass(Queue.class);
        verify(queues).save(queueCaptor.capture());
        assertEquals(5, queueCaptor.getValue().getPosition());
        assertSame(created, queueCaptor.getValue().getAppointment());
        assertSame(department, queueCaptor.getValue().getDepartment());
        verify(auditLogsService).record(eq("WALK_IN_REGISTERED"), any(), eq("Appointment"), any(), any());
    }

    @Test
    void walkInWithUnknownPatientReturnsNotFound() {
        when(patients.findById(any())).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.registerWalkIn(jwt(staffAuthId), new WalkInRequest(UUID.randomUUID(), department.getDepartmentId())));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(appointments, never()).save(any());
    }

    @Test
    void checkInMovesBookedAppointmentToWaitingAndQueuesIt() {
        Appointment booked = appointment(AppointmentStatus.BOOKED, patient, currentStaff);
        when(appointments.findById(booked.getAppointmentId())).thenReturn(Optional.of(booked));

        var response = service.checkInDto(jwt(staffAuthId), booked.getAppointmentId());

        assertEquals("WAITING", response.appointmentStatus());
        assertEquals(AppointmentStatus.WAITING, booked.getAppointmentStatus());
        assertNotNull(booked.getCheckInTime());
        verify(queues).save(any(Queue.class));
        verify(auditLogsService).record(eq("APPOINTMENT_CHECKED_IN"), any(), eq("Appointment"), any(), any());
    }

    @Test
    void checkInRejectsAppointmentThatIsNotBooked() {
        Appointment waiting = appointment(AppointmentStatus.WAITING, patient, currentStaff);
        when(appointments.findById(waiting.getAppointmentId())).thenReturn(Optional.of(waiting));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.checkIn(jwt(staffAuthId), waiting.getAppointmentId()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(queues, never()).save(any());
    }

    @Test
    void patientCanCheckInOwnAppointmentWithoutStaffProfile() {
        Appointment booked = appointment(AppointmentStatus.BOOKED, patient, currentStaff);
        UUID unknownStaffAuthId = UUID.randomUUID();
        when(staff.findByAuthUserId(unknownStaffAuthId)).thenReturn(Optional.empty());
        when(appointments.findById(booked.getAppointmentId())).thenReturn(Optional.of(booked));

        assertDoesNotThrow(() -> service.checkIn(jwt(patientAuthId), booked.getAppointmentId()));
        assertEquals(AppointmentStatus.WAITING, booked.getAppointmentStatus());
    }

    @Test
    void anotherPatientCannotCheckInSomeonesAppointment() {
        Appointment booked = appointment(AppointmentStatus.BOOKED, patient, currentStaff);
        UUID stranger = UUID.randomUUID();
        when(staff.findByAuthUserId(stranger)).thenReturn(Optional.empty());
        when(appointments.findById(booked.getAppointmentId())).thenReturn(Optional.of(booked));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.checkIn(jwt(stranger), booked.getAppointmentId()));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals(AppointmentStatus.BOOKED, booked.getAppointmentStatus());
    }

    @Test
    void callNextPrefersUrgentThenEarliestBookingAndNotifiesPatient() {
        Appointment normal = appointment(AppointmentStatus.WAITING, patient, currentStaff);
        normal.setBookedAt(LocalDateTime.of(2030, 1, 1, 8, 0));
        Appointment urgent = appointment(AppointmentStatus.WAITING, patient, currentStaff);
        urgent.setPriority(Priority.URGENT);
        urgent.setBookedAt(LocalDateTime.of(2030, 1, 1, 9, 0));
        when(queues.findByDepartmentAndAppointmentStatus(department, AppointmentStatus.WAITING))
                .thenReturn(List.of(queue(normal, department, 1), queue(urgent, department, 2)));

        var response = service.callNextDto(jwt(staffAuthId));

        assertEquals("INCONSULTATION", response.appointmentStatus());
        assertEquals(AppointmentStatus.INCONSULTATION, urgent.getAppointmentStatus());
        assertEquals(AppointmentStatus.WAITING, normal.getAppointmentStatus());
        assertNotNull(urgent.getCalledTime());

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).createNotification(notificationCaptor.capture());
        assertSame(patient, notificationCaptor.getValue().getPatient());
        assertSame(urgent, notificationCaptor.getValue().getAppointment());
        assertFalse(notificationCaptor.getValue().getIsRead());
        verify(auditLogsService).record(eq("QUEUE_PATIENT_CALLED"), any(), eq("Appointment"), any(), any());
    }

    @Test
    void callNextWithNoWaitingPatientsReturnsNotFound() {
        when(queues.findByDepartmentAndAppointmentStatus(department, AppointmentStatus.WAITING))
                .thenReturn(List.of());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.callNext(jwt(staffAuthId)));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(appointments, never()).save(any());
    }

    @Test
    void prioritiseMarksAppointmentUrgentAndReturnsQueueDto() {
        Appointment waiting = appointment(AppointmentStatus.WAITING, patient, currentStaff);
        Queue entry = queue(waiting, department, 3);
        when(queues.findById(entry.getQueueId())).thenReturn(Optional.of(entry));

        QueueResponse response = service.prioritiseDto(jwt(staffAuthId), entry.getQueueId(),
                new PriorityRequest("Patient requested priority"));

        assertEquals(Priority.URGENT, waiting.getPriority());
        assertEquals(entry.getQueueId(), response.queueId());
        verify(appointments).save(waiting);
        verify(auditLogsService).record(eq("QUEUE_PRIORITISED"), eq("Patient requested priority"),
                eq("Queue"), eq(entry.getQueueId()), any());
    }

    @Test
    void completeMarksConsultationCompletedOnlyWithinTheDepartment() {
        Appointment inConsultation = appointment(AppointmentStatus.INCONSULTATION, patient, currentStaff);
        when(appointments.findById(inConsultation.getAppointmentId())).thenReturn(Optional.of(inConsultation));

        var response = service.completeDto(jwt(staffAuthId), inConsultation.getAppointmentId());

        assertEquals("COMPLETED", response.appointmentStatus());
        assertEquals(AppointmentStatus.COMPLETED, inConsultation.getAppointmentStatus());
        assertNotNull(inConsultation.getCompletedTime());
    }

    @Test
    void completeRejectsAppointmentFromAnotherDepartment() {
        Department other = department("Other department");
        Staff foreignStaff = staff(UUID.randomUUID(), true);
        foreignStaff.setDepartment(other);
        Appointment inConsultation = appointment(AppointmentStatus.INCONSULTATION, patient, foreignStaff);
        when(appointments.findById(inConsultation.getAppointmentId())).thenReturn(Optional.of(inConsultation));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.complete(jwt(staffAuthId), inConsultation.getAppointmentId()));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void cancelAllowsOnlyTheOwningPatientAndRequiresBookedStatus() {
        Appointment booked = appointment(AppointmentStatus.BOOKED, patient, currentStaff);
        when(appointments.findSlotIdByAppointmentId(booked.getAppointmentId())).thenReturn(Optional.empty());
        when(appointments.findByIdForUpdate(booked.getAppointmentId())).thenReturn(Optional.of(booked));
        when(appointments.saveAndFlush(booked)).thenReturn(booked);

        assertEquals("CANCELLED", service.cancelPatientDto(jwt(patientAuthId), booked.getAppointmentId()).appointmentStatus());

        Appointment second = appointment(AppointmentStatus.BOOKED, patient, currentStaff);
        when(appointments.findSlotIdByAppointmentId(second.getAppointmentId())).thenReturn(Optional.empty());
        when(appointments.findByIdForUpdate(second.getAppointmentId())).thenReturn(Optional.of(second));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.cancelPatient(jwt(UUID.randomUUID()), second.getAppointmentId()));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void queuePositionIsAvailableToOwnerAndDeniedToOthers() {
        Appointment waiting = appointment(AppointmentStatus.WAITING, patient, currentStaff);
        Queue entry = queue(waiting, department, 2);
        when(appointments.findById(waiting.getAppointmentId())).thenReturn(Optional.of(waiting));
        when(queues.findByAppointment(waiting)).thenReturn(Optional.of(entry));

        var response = service.queuePositionDto(jwt(patientAuthId), waiting.getAppointmentId());
        assertEquals(waiting.getAppointmentId(), response.appointmentId());
        assertEquals(AppointmentStatus.WAITING, response.queueStatus());
        assertNotNull(response.queuePosition());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.queuePosition(jwt(UUID.randomUUID()), waiting.getAppointmentId()));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void writeOperationsAreTransactional() throws Exception {
        for (String method : List.of("updateAvailability", "checkIn", "registerWalkIn",
                "prioritise", "callNext", "complete", "cancelPatient")) {
            var matching = java.util.Arrays.stream(OperationalService.class.getMethods())
                    .filter(candidate -> candidate.getName().equals(method))
                    .filter(candidate -> candidate.isAnnotationPresent(
                            org.springframework.transaction.annotation.Transactional.class)
                            || candidate.isAnnotationPresent(jakarta.transaction.Transactional.class))
                    .findFirst();
            assertTrue(matching.isPresent(), method + " should be @Transactional");
        }
    }
}
