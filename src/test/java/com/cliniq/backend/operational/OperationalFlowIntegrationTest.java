package com.cliniq.backend.operational;

import com.cliniq.backend.controller.OperationalController;
import com.cliniq.backend.dto.AppointmentResponse;
import com.cliniq.backend.dto.QueuePositionResponse;
import com.cliniq.backend.dto.WalkInRequest;
import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.enums.AvailabilityStatus;
import com.cliniq.backend.enums.Priority;
import com.cliniq.backend.enums.StaffRole;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Department;
import com.cliniq.backend.model.Patient;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Exercises the real controller + service together with mocked repositories. */
class OperationalFlowIntegrationTest {
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final QueueRepository queues = mock(QueueRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final ScheduleRepository schedules = mock(ScheduleRepository.class);
    private final PatientRepository patients = mock(PatientRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final AccessPolicy accessPolicy = mock(AccessPolicy.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final AuditLogsService auditLogsService = mock(AuditLogsService.class);
    private final JsonMapper mapper = JsonMapper.builder().build();

    private final UUID staffAuthId = UUID.randomUUID();
    private final UUID patientAuthId = UUID.randomUUID();
    private OperationalController controller;
    private Staff currentStaff;
    private Patient patient;
    private Department department;

    private Jwt jwt(UUID subject) {
        return Jwt.withTokenValue("test").header("alg", "test").subject(subject.toString()).build();
    }

    @BeforeEach
    void setup() {
        department = new Department();
        department.setDepartmentId(UUID.randomUUID());
        department.setName("General care");
        department.setOperatingHours("08:00 to 16:00");

        currentStaff = new Staff();
        currentStaff.setStaffId(UUID.randomUUID());
        currentStaff.setAuthUserId(staffAuthId);
        currentStaff.setFirstName("Ada");
        currentStaff.setLastName("Nurse");
        currentStaff.setStaffRole(StaffRole.NURSE);
        currentStaff.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        currentStaff.setActive(true);
        currentStaff.setDepartment(department);

        patient = new Patient();
        patient.setPatientID(UUID.randomUUID());
        patient.setAuthUserId(patientAuthId);
        patient.setPatientName("Kabo");
        patient.setPatientSurname("Doe");
        patient.setPatientEmail("kabo@example.com");
        patient.setNationalID("Omang-123");
        patient.setPhone("+26770000000");

        when(staff.findByAuthUserId(staffAuthId)).thenReturn(Optional.of(currentStaff));
        when(staff.save(any(Staff.class))).thenAnswer(call -> call.getArgument(0));
        when(appointments.save(any(Appointment.class))).thenAnswer(call -> call.getArgument(0));
        when(queues.save(any(com.cliniq.backend.model.Queue.class))).thenAnswer(call -> call.getArgument(0));
        when(queues.countByDepartmentAndAppointmentStatus(any(), any())).thenReturn(0L);

        var service = new OperationalService(appointments, queues, staff, patients, departments,
                accessPolicy, notificationService, auditLogsService, schedules);
        controller = new OperationalController(service);
    }

    @Test
    void walkInReturnsSafeDtoWithoutInternalAuthIds() {
        when(patients.findById(patient.getPatientID())).thenReturn(Optional.of(patient));
        when(departments.findById(department.getDepartmentId())).thenReturn(Optional.of(department));

        AppointmentResponse response = controller.walkIn(jwt(staffAuthId),
                new WalkInRequest(patient.getPatientID(), department.getDepartmentId()));

        ObjectNode json = mapper.valueToTree(response);
        assertEquals(AppointmentStatus.WAITING.name(), json.get("appointmentStatus").asText());
        assertEquals(patient.getPatientID().toString(), json.get("patient").get("patientID").asText());
        assertEquals(department.getDepartmentId().toString(), json.get("staff").get("department").get("departmentId").asText());
        assertFalse(json.get("staff").has("authUserId"));
        assertFalse(json.get("patient").has("authUserId"));
        assertFalse(json.get("staff").get("department").has("authUserId"));
    }

    @Test
    void checkInReturnsWaitingAppointmentDto() {
        Appointment booked = new Appointment();
        booked.setAppointmentId(UUID.randomUUID());
        booked.setAppointmentStatus(AppointmentStatus.BOOKED);
        booked.setPriority(Priority.NORMAL);
        booked.setPatient(patient);
        booked.setStaff(currentStaff);
        booked.setCreatedAt(LocalDateTime.of(2030, 1, 1, 8, 0));
        when(appointments.findById(booked.getAppointmentId())).thenReturn(Optional.of(booked));

        AppointmentResponse response = controller.checkIn(jwt(staffAuthId), booked.getAppointmentId());

        ObjectNode json = mapper.valueToTree(response);
        assertEquals(AppointmentStatus.WAITING.name(), json.get("appointmentStatus").asText());
        assertFalse(json.has("qrCode"));
        assertFalse(json.get("patient").has("authUserId"));
    }

    @Test
    void queuePositionReturnsDtoWithAppointmentStatus() {
        Appointment waiting = new Appointment();
        waiting.setAppointmentId(UUID.randomUUID());
        waiting.setAppointmentStatus(AppointmentStatus.WAITING);
        waiting.setPriority(Priority.NORMAL);
        waiting.setPatient(patient);
        waiting.setStaff(currentStaff);
        waiting.setCreatedAt(LocalDateTime.of(2030, 1, 1, 8, 0));

        com.cliniq.backend.model.Queue entry = new com.cliniq.backend.model.Queue();
        entry.setQueueId(UUID.randomUUID());
        entry.setAppointment(waiting);
        entry.setDepartment(department);
        entry.setPosition(3);
        entry.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        when(appointments.findById(waiting.getAppointmentId())).thenReturn(Optional.of(waiting));
        when(queues.findByAppointment(waiting)).thenReturn(Optional.of(entry));

        QueuePositionResponse response = controller.queuePosition(jwt(patientAuthId), waiting.getAppointmentId());

        ObjectNode json = mapper.valueToTree(response);
        assertEquals(waiting.getAppointmentId().toString(), json.get("appointmentId").asText());
        assertEquals(AppointmentStatus.WAITING.name(), json.get("queueStatus").asText());
        assertFalse(json.toString().contains("authUserId"));
    }
}
