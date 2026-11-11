package com.cliniq.backend.service;

import com.cliniq.backend.dto.AvailabilityUpdateRequest;
import com.cliniq.backend.dto.PriorityRequest;
import com.cliniq.backend.dto.WalkInRequest;
import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.enums.AvailabilityStatus;
import com.cliniq.backend.enums.Priority;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Department;
import com.cliniq.backend.model.Patient;
import com.cliniq.backend.model.Queue;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.AppointmentRepository;
import com.cliniq.backend.repository.DepartmentRepository;
import com.cliniq.backend.repository.PatientRepository;
import com.cliniq.backend.repository.QueueRepository;
import com.cliniq.backend.repository.StaffRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
public class OperationalService {
    private final AppointmentRepository appointments;
    private final QueueRepository queues;
    private final StaffRepository staff;
    private final PatientRepository patients;
    private final DepartmentRepository departments;
    private final AccessPolicy accessPolicy;
    private final NotificationService notificationService;

    public OperationalService(AppointmentRepository appointments, QueueRepository queues,
                              StaffRepository staff, PatientRepository patients,
                              DepartmentRepository departments, AccessPolicy accessPolicy, NotificationService notificationService) {
        this.appointments = appointments;
        this.queues = queues;
        this.staff = staff;
        this.patients = patients;
        this.departments = departments;
        this.accessPolicy = accessPolicy;
        this.notificationService = notificationService;
    }

    @Transactional
    public Staff updateAvailability(Jwt jwt, AvailabilityUpdateRequest request) {
        Staff current = requireStaff(jwt);
        current.setAvailabilityStatus(request.status());
        return staff.save(current);
    }

    @Transactional
    public Appointment checkIn(Jwt jwt, UUID appointmentId) {
        Appointment appointment = appointment(appointmentId);
        Staff current = staff.findByAuthUserId(AccessPolicy.subject(jwt)).orElse(null);
        if (current == null && !ownsAppointment(jwt, appointment)) {
            throw forbidden("Only the patient or clinic staff can check in this appointment.");
        }
        if (appointment.getAppointmentStatus() != AppointmentStatus.BOOKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only booked appointments can be checked in.");
        }
        appointment.setAppointmentStatus(AppointmentStatus.WAITING);
        appointment.setCheckInTime(LocalDateTime.now());
        createQueue(appointment, appointment.getStaff().getDepartment());
        return appointments.save(appointment);
    }

    @Transactional
    public Appointment registerWalkIn(Jwt jwt, WalkInRequest request) {
        Staff current = requireStaff(jwt);
        Patient patient = patients.findById(request.patientId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found."));
        Department department = departments.findById(request.departmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found."));
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setStaff(current);
        appointment.setAppointmentStatus(AppointmentStatus.WAITING);
        appointment.setPriority(Priority.NORMAL);
        appointment.setCheckInTime(LocalDateTime.now());
        Appointment saved = appointments.save(appointment);
        createQueue(saved, department);
        return saved;
    }

    @Transactional
    public Queue prioritise(Jwt jwt, UUID queueId, PriorityRequest request) {
        requireStaff(jwt);
        Queue queue = queue(queueId);
        Appointment appointment = queue.getAppointment();
        appointment.setPriority(Priority.URGENT);
        appointments.save(appointment);
        return queues.save(queue);
    }

    @Transactional
    public Appointment callNext(Jwt jwt) {
        Staff current = requireStaff(jwt);
        List<Queue> waiting = queues.findByDepartmentAndAppointmentStatus(
                current.getDepartment(), AppointmentStatus.WAITING);
        Queue next = waiting.stream()
                .sorted((left, right) -> {
                    int priority = right.getAppointment().getPriority().compareTo(left.getAppointment().getPriority());
                    return priority != 0 ? priority : bookingMoment(left.getAppointment()).compareTo(bookingMoment(right.getAppointment()));
                })
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No patients are waiting."));
        Appointment appointment = next.getAppointment();
        appointment.setAppointmentStatus(AppointmentStatus.INCONSULTATION);
        appointment.setCalledTime(LocalDateTime.now());

        com.cliniq.backend.model.Notification notification = new com.cliniq.backend.model.Notification();
        notification.setPatient(appointment.getPatient());
        notification.setAppointment(appointment);
        notification.setMessage("You are next! Please proceed to the consultation room");
        notification.setType("QUEUE_Called");
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationService.createNotification(notification);

        return appointments.save(appointment);
    }

    @Transactional
    public Appointment complete(Jwt jwt, UUID appointmentId) {
        Staff current = requireStaff(jwt);
        Appointment appointment = appointment(appointmentId);
        if (appointment.getStaff() == null || !appointment.getStaff().getDepartment().equals(current.getDepartment())) {
            throw forbidden("This appointment is outside your department.");
        }
        if (appointment.getAppointmentStatus() != AppointmentStatus.INCONSULTATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only consultations can be completed.");
        }
        appointment.setAppointmentStatus(AppointmentStatus.COMPLETED);
        appointment.setCompletedTime(LocalDateTime.now());
        return appointments.save(appointment);
    }

    @Transactional
    public Appointment cancelPatient(Jwt jwt, UUID appointmentId) {
        Appointment appointment = appointment(appointmentId);
        if (!ownsAppointment(jwt, appointment)) {
            throw forbidden("You can only cancel your own appointments.");
        }
        if (appointment.getAppointmentStatus() != AppointmentStatus.BOOKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only booked appointments can be cancelled.");
        }
        appointment.setAppointmentStatus(AppointmentStatus.CANCELLED);
        return appointments.save(appointment);
    }

    public Queue queuePosition(Jwt jwt, UUID appointmentId) {
        Appointment appointment = appointment(appointmentId);
        if (!ownsAppointment(jwt, appointment)) {
            throw forbidden("You can only view your own queue position.");
        }
        return queues.findByAppointment(appointment)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This appointment is not in a queue."));
    }

    // Queue order is booking time, falling back to check-in/creation for records without a bookedAt (e.g. walk-ins).
    private static LocalDateTime bookingMoment(Appointment appointment) {
        if (appointment.getBookedAt() != null) return appointment.getBookedAt();
        if (appointment.getCheckInTime() != null) return appointment.getCheckInTime();
        return appointment.getCreatedAt();
    }

    private Queue createQueue(Appointment appointment, Department department) {
        Queue queue = new Queue();
        queue.setAppointment(appointment);
        queue.setDepartment(department);
        queue.setPosition((int) queues.countByDepartmentAndAppointmentStatus(department, AppointmentStatus.WAITING) + 1);
        queue.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        queue.setTimeStamp(LocalTime.now());
        return queues.save(queue);
    }

    private Appointment appointment(UUID id) {
        return appointments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found."));
    }

    private Queue queue(UUID id) {
        return queues.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Queue entry not found."));
    }

    private Staff requireStaff(Jwt jwt) {
        UUID userId = AccessPolicy.subject(jwt);
        return staff.findByAuthUserId(userId)
                .filter(profile -> Boolean.TRUE.equals(profile.getActive()))
                .orElseThrow(() -> forbidden("An active staff account is required."));
    }

    private boolean ownsAppointment(Jwt jwt, Appointment appointment) {
        UUID userId = AccessPolicy.subject(jwt);
        return appointment.getPatient() != null
                && userId.equals(appointment.getPatient().getAuthUserId());
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
