package com.cliniq.backend.operational;

import com.cliniq.backend.enums.*;
import com.cliniq.backend.model.*;
import com.cliniq.backend.model.Queue;
import com.cliniq.backend.repository.*;
import com.cliniq.backend.service.*;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OperationalFixture {
    final AppointmentRepository appointments = mock(AppointmentRepository.class);
    final QueueRepository queues = mock(QueueRepository.class);
    final ScheduleRepository schedules = mock(ScheduleRepository.class);
    final StaffRepository staff = mock(StaffRepository.class);
    final PatientRepository patients = mock(PatientRepository.class);
    final DepartmentRepository departments = mock(DepartmentRepository.class);
    final AccessPolicy policy = mock(AccessPolicy.class);
    final NotificationService notifications = mock(NotificationService.class);
    final AuditLogsService audit = mock(AuditLogsService.class);
    final OperationalService service = new OperationalService(appointments, queues, staff, patients,
            departments, policy, notifications, audit, schedules);
    final UUID userId = UUID.randomUUID();
    final Jwt jwt = jwt(userId);
    final Department department = department();
    final Staff doctor = new Staff();
    final Patient patient = new Patient();
    final List<Queue> entries = new ArrayList<>();
    final LocalDateTime moment = LocalDateTime.of(2030, 1, 1, 9, 0);

    OperationalFixture() {
        doctor.setStaffId(UUID.randomUUID());
        doctor.setActive(true);
        doctor.setStaffRole(StaffRole.DOCTOR);
        doctor.setDepartment(department);
        patient.setAuthUserId(userId);
        patient.setPatientID(UUID.randomUUID());
        when(staff.findByAuthUserId(userId)).thenReturn(Optional.of(doctor));
        when(appointments.save(any())).thenAnswer(call -> call.getArgument(0));
        when(appointments.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(queues.findByDepartmentAndAppointmentStatus(any(), any())).thenAnswer(call -> entries.stream()
                .filter(q -> q.getDepartment() == call.getArgument(0))
                .filter(q -> q.getAppointment().getAppointmentStatus() == call.getArgument(1)).toList());
        when(queues.countByDepartmentAndAppointmentStatus(any(), any())).thenAnswer(call -> entries.stream()
                .filter(q -> q.getDepartment() == call.getArgument(0))
                .filter(q -> q.getAppointment().getAppointmentStatus() == call.getArgument(1)).count());
    }

    Queue entry(long id, AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(new UUID(0, id));
        appointment.setAppointmentStatus(status);
        appointment.setPriority(Priority.NORMAL);
        appointment.setBookedAt(moment);
        appointment.setPatient(patient);
        appointment.setStaff(doctor);
        Queue queue = new Queue();
        queue.setQueueId(new UUID(1, id));
        queue.setAppointment(appointment);
        queue.setDepartment(department);
        queue.setPosition(99);
        entries.add(queue);
        when(appointments.findById(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        when(queues.findByAppointment(appointment)).thenReturn(Optional.of(queue));
        return queue;
    }

    Schedule slot(Appointment appointment) {
        Schedule slot = new Schedule();
        slot.setSlotId(UUID.randomUUID());
        slot.setStaff(doctor);
        slot.setSlotStatus(SlotStatus.BOOKED);
        appointment.setSlot(slot);
        when(appointments.findSlotIdByAppointmentId(appointment.getAppointmentId())).thenReturn(Optional.of(slot.getSlotId()));
        when(schedules.findByIdForUpdate(slot.getSlotId())).thenReturn(Optional.of(slot));
        return slot;
    }

    static Jwt jwt(UUID id) {
        return Jwt.withTokenValue("test").header("alg", "test").subject(id.toString()).build();
    }

    static Department department() {
        Department result = new Department();
        result.setDepartmentId(UUID.randomUUID());
        return result;
    }
}
