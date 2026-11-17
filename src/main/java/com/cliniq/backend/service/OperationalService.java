package com.cliniq.backend.service;

import com.cliniq.backend.dto.AppointmentResponse;
import com.cliniq.backend.dto.AvailabilityUpdateRequest;
import com.cliniq.backend.dto.PriorityRequest;
import com.cliniq.backend.dto.QueueResponse;
import com.cliniq.backend.dto.StaffResponse;
import com.cliniq.backend.dto.WalkInRequest;
import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.enums.AvailabilityStatus;
import com.cliniq.backend.enums.Priority;
import com.cliniq.backend.enums.SlotStatus;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Department;
import com.cliniq.backend.model.Patient;
import com.cliniq.backend.model.Queue;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.model.Schedule;
import com.cliniq.backend.repository.AppointmentRepository;
import com.cliniq.backend.repository.DepartmentRepository;
import com.cliniq.backend.repository.PatientRepository;
import com.cliniq.backend.repository.QueueRepository;
import com.cliniq.backend.repository.StaffRepository;
import com.cliniq.backend.repository.ScheduleRepository;
import org.springframework.transaction.annotation.Transactional;
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
    private final ScheduleRepository schedules;
    private final QueueRepository queues;
    private final StaffRepository staff;
    private final PatientRepository patients;
    private final DepartmentRepository departments;
    private final AccessPolicy accessPolicy;
    private final NotificationService notificationService;
    private final AuditLogsService auditLogsService;

    public OperationalService(AppointmentRepository appointments, QueueRepository queues,
                              StaffRepository staff, PatientRepository patients,
                              DepartmentRepository departments, AccessPolicy accessPolicy,
                              NotificationService notificationService, AuditLogsService auditLogsService,
                              ScheduleRepository schedules) {
        this.appointments = appointments;
        this.schedules = schedules;
        this.queues = queues;
        this.staff = staff;
        this.patients = patients;
        this.departments = departments;
        this.accessPolicy = accessPolicy;
        this.notificationService = notificationService;
        this.auditLogsService = auditLogsService;
    }

    @Transactional
    public Staff updateAvailability(Jwt jwt, AvailabilityUpdateRequest request) {
        Staff current = requireStaff(jwt);
        current.setAvailabilityStatus(request.status());
        Staff saved = staff.save(current);
        auditLogsService.record("STAFF_AVAILABILITY_UPDATED", request.status().name(), "Staff", saved.getStaffId(), jwt);
        return saved;
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
        Appointment saved = appointments.save(appointment);
        auditLogsService.record("APPOINTMENT_CHECKED_IN", "Appointment checked in", "Appointment", saved.getAppointmentId(), jwt);
        return saved;
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
        auditLogsService.record("WALK_IN_REGISTERED", "Walk-in patient added to queue", "Appointment", saved.getAppointmentId(), jwt);
        return saved;
    }

    @Transactional
    public AppointmentResponse markNoShowDto(Jwt jwt, UUID appointmentId) {
        requireStaff(jwt);
        Appointment appointment = appointment(appointmentId);
        appointment.setAppointmentStatus(AppointmentStatus.NOSHOW);
        Appointment saved = appointments.save(appointment);
        return AppointmentResponse.from(saved);
    }

    @Transactional
    public AppointmentResponse cancelByStaffDto(Jwt jwt, UUID appointmentId) {
        requireStaff(jwt);
        Appointment appointment = appointment(appointmentId);
        appointment.setAppointmentStatus(AppointmentStatus.CANCELLED);
        Appointment saved = appointments.save(appointment);
        return AppointmentResponse.from(saved);
    }

    @Transactional
    public void broadcastDelay(Jwt jwt, String delayMessage) {
        Staff current = requireStaff(jwt);

        List<Queue> waiting = queues.findByDepartmentAndAppointmentStatus(
                current.getDepartment(), AppointmentStatus.WAITING);

        for (Queue q : waiting) {
            notificationService.notifyPatient(
                    q.getAppointment().getPatient(),
                    q.getAppointment(),
                    "DELAY_ALERT",
                    delayMessage
            );
        }
    }

    @Transactional
    public Queue prioritise(Jwt jwt, UUID queueId, PriorityRequest request) {
        requireStaff(jwt);
        Queue queue = queue(queueId);
        Appointment appointment = queue.getAppointment();
        appointment.setPriority(Priority.URGENT);
        appointments.save(appointment);
        Queue saved = queues.save(queue);
        auditLogsService.record("QUEUE_PRIORITISED", request.reason(), "Queue", saved.getQueueId(), jwt);
        return saved;
    }

    @Transactional
    public Appointment callNext(Jwt jwt) {
        Staff current = requireStaff(jwt);
        List<Queue> waiting = queues.findByDepartmentAndAppointmentStatus(
                current.getDepartment(), AppointmentStatus.WAITING);
        Queue next = waiting.stream()
                .sorted(QueueOrdering.NEXT_PATIENT)
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

        Appointment saved = appointments.save(appointment);
        auditLogsService.record("QUEUE_PATIENT_CALLED", "Next patient called", "Appointment", saved.getAppointmentId(), jwt);
        return saved;
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
        Appointment saved = appointments.save(appointment);
        auditLogsService.record("CONSULTATION_COMPLETED", "Consultation completed", "Appointment", saved.getAppointmentId(), jwt);
        return saved;
    }

    @Transactional
    public Appointment cancelPatient(Jwt jwt, UUID appointmentId) {
        AccessPolicy.subject(jwt);
        return cancel(jwt, appointmentId, false);
    }

    @Transactional
    public Appointment cancelAdmin(Jwt jwt, UUID appointmentId) {
        accessPolicy.requireAdmin(jwt);
        return cancel(jwt, appointmentId, true);
    }

    private Appointment cancel(Jwt jwt, UUID appointmentId, boolean admin) {
        // Match booking's slot-first lock order. Do not load/mutate the appointment before
        // this lock: a concurrent cancellation may commit while we wait for the slot.
        Schedule slot = appointments.findSlotIdByAppointmentId(appointmentId)
                .map(id -> schedules.findByIdForUpdate(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found.")))
                .orElse(null);
        Appointment appointment = appointments.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found."));
        if (!admin && !ownsAppointment(jwt, appointment)) {
            throw forbidden("You can only cancel your own appointments.");
        }
        if (appointment.getAppointmentStatus() != AppointmentStatus.BOOKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only booked appointments can be cancelled.");
        }
        appointment.setAppointmentStatus(AppointmentStatus.CANCELLED);
        Appointment saved = appointments.saveAndFlush(appointment);
        if (slot != null && slot.getSlotStatus() == SlotStatus.BOOKED
                && appointments.countOccupyingBySlotId(slot.getSlotId()) < slot.effectiveCapacity()) {
            slot.setSlotStatus(SlotStatus.OPEN);
            schedules.saveAndFlush(slot);
        }
        auditLogsService.record("APPOINTMENT_CANCELLED", admin ? "Administrator cancelled appointment" : "Patient cancelled appointment",
                        "Appointment", saved.getAppointmentId(), jwt);
        return saved;
    }

    @Transactional(readOnly = true)
    public QueuePositionResponse queuePosition(Jwt jwt, UUID appointmentId) {
        AccessPolicy.subject(jwt);
        Appointment appointment = appointment(appointmentId);
        if (!ownsAppointment(jwt, appointment)) {
            throw forbidden("You can only view your own queue position.");
        }
        Queue queue = queues.findByAppointment(appointment)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This appointment is not in a queue."));
        AppointmentStatus status = appointment.getAppointmentStatus();
        if (status != AppointmentStatus.WAITING) {
            Integer wait = status == AppointmentStatus.INCONSULTATION || status == AppointmentStatus.COMPLETED
                    || status == AppointmentStatus.CANCELLED || status == AppointmentStatus.NOSHOW ? 0 : null;
            return new QueuePositionResponse(appointmentId, null, status, wait);
        }
        // Count those ahead rather than relying on the stored position or list membership:
        // a concurrent call may remove this entry after we read its WAITING status.
        int position = 1 + (int) queues.findByDepartmentAndAppointmentStatus(queue.getDepartment(), AppointmentStatus.WAITING)
                .stream().filter(other -> QueueOrdering.NEXT_PATIENT.compare(other, queue) < 0).count();
        double averageMinutes = averageConsultationMinutes(queue.getDepartment());
        long inProgress = queues.countByDepartmentAndAppointmentStatus(queue.getDepartment(), AppointmentStatus.INCONSULTATION);
        // Approximation: one average consultation per waiter ahead and per occupied consultation.
        // This deliberately does not promise an exact ETA or model parallel doctor throughput.
        int estimatedWait = (int) Math.min(Integer.MAX_VALUE,
                Math.ceil((position - 1.0 + inProgress) * averageMinutes));
        return new QueuePositionResponse(appointmentId, position, status, estimatedWait);
    }

    @Transactional
    public StaffResponse updateAvailabilityDto(Jwt jwt, AvailabilityUpdateRequest request) {
        return StaffResponse.from(updateAvailability(jwt, request));
    }

    @Transactional
    public AppointmentResponse checkInDto(Jwt jwt, UUID appointmentId) {
        return AppointmentResponse.from(checkIn(jwt, appointmentId));
    }

    @Transactional
    public AppointmentResponse registerWalkInDto(Jwt jwt, WalkInRequest request) {
        return AppointmentResponse.from(registerWalkIn(jwt, request));
    }

    @Transactional
    public QueueResponse prioritiseDto(Jwt jwt, UUID queueId, PriorityRequest request) {
        return QueueResponse.from(prioritise(jwt, queueId, request));
    }

    @Transactional
    public AppointmentResponse callNextDto(Jwt jwt) {
        return AppointmentResponse.from(callNext(jwt));
    }

    @Transactional
    public AppointmentResponse completeDto(Jwt jwt, UUID appointmentId) {
        return AppointmentResponse.from(complete(jwt, appointmentId));
    }

    @Transactional
    public AppointmentResponse cancelPatientDto(Jwt jwt, UUID appointmentId) {
        return AppointmentResponse.from(cancelPatient(jwt, appointmentId));
    }

    @Transactional
    public QueueResponse queuePositionDto(Jwt jwt, UUID appointmentId) {
        return QueueResponse.from(queuePosition(jwt, appointmentId));
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
