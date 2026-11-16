package com.cliniq.backend.service;

import com.cliniq.backend.dto.AvailabilityUpdateRequest;
import com.cliniq.backend.dto.PriorityRequest;
import com.cliniq.backend.dto.QueueEntryResponse;
import com.cliniq.backend.dto.StaffAppointmentResponse;
import com.cliniq.backend.dto.WalkInRequest;
import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.enums.AvailabilityStatus;
import com.cliniq.backend.enums.Priority;
import com.cliniq.backend.enums.SlotStatus;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Department;
import com.cliniq.backend.model.Notification;
import com.cliniq.backend.model.Patient;
import com.cliniq.backend.model.Queue;
import com.cliniq.backend.model.Schedule;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.AppointmentRepository;
import com.cliniq.backend.repository.DepartmentRepository;
import com.cliniq.backend.repository.PatientRepository;
import com.cliniq.backend.repository.QueueRepository;
import com.cliniq.backend.repository.ScheduleRepository;
import com.cliniq.backend.repository.StaffRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class OperationalService {
    private final AppointmentRepository appointments;
    private final QueueRepository queues;
    private final StaffRepository staff;
    private final PatientRepository patients;
    private final DepartmentRepository departments;
    private final NotificationService notificationService;
    private final ScheduleRepository schedules;

    private static final int MINUTES_PER_PATIENT = 15;

    private static final Comparator<Queue> FIFO_QUEUE_ORDER = Comparator
            .comparing((Queue q) -> q.getAppointment().getPriority() == Priority.URGENT ? 0 : 1)
            .thenComparing(q -> q.getAppointment().getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(q -> q.getAppointment().getAppointmentId());

    @Autowired
    public OperationalService(AppointmentRepository appointments, QueueRepository queues,
                              StaffRepository staff, PatientRepository patients,
                              DepartmentRepository departments, NotificationService notificationService,
                              ScheduleRepository schedules) {
        this.appointments = appointments;
        this.queues = queues;
        this.staff = staff;
        this.patients = patients;
        this.departments = departments;
        this.notificationService = notificationService;
        this.schedules = schedules;
    }

    public OperationalService(AppointmentRepository appointments, QueueRepository queues,
                              StaffRepository staff, PatientRepository patients,
                              DepartmentRepository departments, NotificationService notificationService) {
        this(appointments, queues, staff, patients, departments, notificationService, null);
    }

    @Transactional
    public Staff updateAvailability(Jwt jwt, AvailabilityUpdateRequest request) {
        Staff current = requireStaff(jwt);
        current.setAvailabilityStatus(request.status());
        return staff.save(current);
    }

    public List<StaffAppointmentResponse> getStaffAppointments(Jwt jwt) {
        Staff current = requireStaff(jwt);
        return appointments.findByStaffIdOrderByCreatedAtAsc(current.getStaffId())
                .stream()
                .map(this::toStaffAppointmentResponse)
                .toList();
    }

    @Transactional
    public List<QueueEntryResponse> getDepartmentQueue(Jwt jwt) {
        Staff current = requireStaff(jwt);
        if (current.getDepartment() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Staff member is not assigned to a department.");
        }
        UUID departmentId = current.getDepartment().getDepartmentId();
        recalculatePositions(departmentId);
        return queues.findActiveByDepartmentId(
                        departmentId,
                        List.of(AppointmentStatus.WAITING, AppointmentStatus.INCONSULTATION))
                .stream()
                .map(this::toQueueEntryResponse)
                .toList();
    }

    @Transactional
    public QueueEntryResponse checkIn(Jwt jwt, UUID appointmentId) {
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
        appointments.save(appointment);

        Department department = appointment.getStaff().getDepartment();
        Queue queue = createQueue(appointment, department);
        recalculatePositions(department.getDepartmentId());
        return toQueueEntryResponse(queue);
    }

    @Transactional
    public QueueEntryResponse registerWalkIn(Jwt jwt, WalkInRequest request) {
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
        Appointment saved = appointments.saveAndFlush(appointment);

        Queue queue = createQueue(saved, department);
        recalculatePositions(department.getDepartmentId());
        return toQueueEntryResponse(queue);
    }

    @Transactional
    public QueueEntryResponse prioritise(Jwt jwt, UUID queueId, PriorityRequest request) {
        requireStaff(jwt);
        Queue queue = queue(queueId);
        Appointment appointment = queue.getAppointment();
        appointment.setPriority(Priority.URGENT);
        appointments.save(appointment);

        recalculatePositions(queue.getDepartment().getDepartmentId());
        return toQueueEntryResponse(queue);
    }

    @Transactional
    public QueueEntryResponse callNext(Jwt jwt) {
        Staff current = requireStaff(jwt);
        if (current.getDepartment() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Staff member is not assigned to a department.");
        }
        UUID departmentId = current.getDepartment().getDepartmentId();
        List<Queue> waiting = queues.findByDepartmentIdAndAppointmentStatus(departmentId, AppointmentStatus.WAITING);

        Queue next = waiting.stream()
                .sorted(FIFO_QUEUE_ORDER)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No patients are waiting."));

        Appointment appointment = next.getAppointment();
        appointment.setAppointmentStatus(AppointmentStatus.INCONSULTATION);
        appointment.setCalledTime(LocalDateTime.now());
        appointments.save(appointment);

        next.setPosition(0);
        queues.save(next);
        recalculatePositions(departmentId);

        Notification notification = new Notification();
        notification.setPatient(appointment.getPatient());
        notification.setAppointment(appointment);
        notification.setMessage(String.format(
                "You are up next in %s! Please proceed to Dr. %s %s's consultation room.",
                current.getDepartment().getName(),
                current.getFirstName(),
                current.getLastName()
        ));
        notification.setType("QUEUE_CALLED");
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationService.createNotification(notification);

        return toQueueEntryResponse(next);
    }

    @Transactional
    public StaffAppointmentResponse complete(Jwt jwt, UUID appointmentId) {
        Staff current = requireStaff(jwt);
        Appointment appointment = appointment(appointmentId);
        if (appointment.getStaff() == null
                || appointment.getStaff().getDepartment() == null
                || current.getDepartment() == null
                || !appointment.getStaff().getDepartment().getDepartmentId()
                .equals(current.getDepartment().getDepartmentId())) {
            throw forbidden("This appointment is outside your department.");
        }
        if (appointment.getAppointmentStatus() != AppointmentStatus.INCONSULTATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only consultations in progress can be completed.");
        }
        appointment.setAppointmentStatus(AppointmentStatus.COMPLETED);
        appointment.setCompletedTime(LocalDateTime.now());
        Appointment saved = appointments.save(appointment);
        recalculatePositions(current.getDepartment().getDepartmentId());
        return toStaffAppointmentResponse(saved);
    }

    @Transactional
    public StaffAppointmentResponse markNoShow(Jwt jwt, UUID appointmentId) {
        Staff current = requireStaff(jwt);
        Appointment appointment = appointment(appointmentId);
        if (appointment.getAppointmentStatus() != AppointmentStatus.WAITING
                && appointment.getAppointmentStatus() != AppointmentStatus.INCONSULTATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only waiting or called appointments can be marked no-show.");
        }
        appointment.setAppointmentStatus(AppointmentStatus.NOSHOW);
        Appointment saved = appointments.save(appointment);
        if (current.getDepartment() != null) {
            recalculatePositions(current.getDepartment().getDepartmentId());
        }
        return toStaffAppointmentResponse(saved);
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
        Appointment saved = appointments.saveAndFlush(appointment);

        Schedule slot = saved.getSlot();
        if (slot != null && schedules != null && slot.getSlotStatus() == SlotStatus.BOOKED) {
            long activeCount = appointments.countBySlot_SlotIdAndAppointmentStatusNot(
                    slot.getSlotId(), AppointmentStatus.CANCELLED);
            if (activeCount < slot.effectiveCapacity()) {
                slot.setSlotStatus(SlotStatus.OPEN);
                schedules.save(slot);
            }
        }
        return saved;
    }

    @Transactional
    public QueueEntryResponse queuePosition(Jwt jwt, UUID appointmentId) {
        Appointment appointment = appointment(appointmentId);
        if (!ownsAppointment(jwt, appointment)) {
            throw forbidden("You can only view your own queue position.");
        }
        Queue queue = queues.findByAppointment(appointment)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This appointment is not in a queue."));
        recalculatePositions(queue.getDepartment().getDepartmentId());
        return toQueueEntryResponse(queue);
    }

    private void recalculatePositions(UUID departmentId) {
        List<Queue> ordered = queues.findByDepartmentIdAndAppointmentStatus(departmentId, AppointmentStatus.WAITING)
                .stream()
                .sorted(FIFO_QUEUE_ORDER)
                .toList();
        for (int i = 0; i < ordered.size(); i++) {
            Queue entry = ordered.get(i);
            int expectedPosition = i + 1;
            if (entry.getPosition() == null || entry.getPosition() != expectedPosition) {
                entry.setPosition(expectedPosition);
                queues.save(entry);
            }
        }
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

    private QueueEntryResponse toQueueEntryResponse(Queue q) {
        Appointment a = q.getAppointment();
        Patient p = a.getPatient();
        int pos = q.getPosition() == null ? 0 : q.getPosition();
        int estimatedWait = a.getAppointmentStatus() == AppointmentStatus.WAITING && pos > 0
                ? pos * MINUTES_PER_PATIENT
                : 0;
        return new QueueEntryResponse(
                q.getQueueId(),
                pos,
                estimatedWait,
                a.getAppointmentId(),
                a.getAppointmentStatus(),
                a.getPriority(),
                p.getPatientID(),
                p.getPatientName(),
                p.getPatientSurname(),
                q.getDepartment() != null ? q.getDepartment().getName() : null,
                a.getCreatedAt(),
                a.getCheckInTime(),
                a.getCalledTime(),
                q.getTimeStamp()
        );
    }

    private StaffAppointmentResponse toStaffAppointmentResponse(Appointment a) {
        Patient p = a.getPatient();
        Schedule s = a.getSlot();
        Queue q = queues.findByAppointment(a).orElse(null);
        return new StaffAppointmentResponse(
                a.getAppointmentId(),
                a.getAppointmentStatus(),
                a.getPriority(),
                p.getPatientID(),
                p.getPatientName(),
                p.getPatientSurname(),
                p.getNationalID(),
                p.getPhone(),
                s != null ? s.getSlotDate() : null,
                s != null ? s.getStartTime() : null,
                s != null ? s.getEndTime() : null,
                a.getCreatedAt(),
                a.getBookedAt(),
                a.getCheckInTime(),
                a.getCalledTime(),
                a.getCompletedTime(),
                q != null ? q.getQueueId() : null,
                q != null ? q.getPosition() : null
        );
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