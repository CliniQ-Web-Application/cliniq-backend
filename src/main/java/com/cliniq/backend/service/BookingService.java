package com.cliniq.backend.service;

import com.cliniq.backend.dto.BookingAvailability;
import com.cliniq.backend.dto.BookingResponse;
import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.enums.Priority;
import com.cliniq.backend.enums.SlotStatus;
import com.cliniq.backend.enums.StaffRole;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Patient;
import com.cliniq.backend.model.Schedule;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.AppointmentRepository;
import com.cliniq.backend.repository.PatientRepository;
import com.cliniq.backend.repository.ScheduleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BookingService {
    private final ScheduleRepository schedules;
    private final AppointmentRepository appointments;
    private final PatientRepository patients;
    private final AccessPolicy accessPolicy;
    private final Clock clock;
    private final NotificationService notificationService;
    private final AuditLogsService auditLogsService;

    @Autowired
    public BookingService(ScheduleRepository schedules, AppointmentRepository appointments,
                          PatientRepository patients, AccessPolicy accessPolicy, Clock clock,
                          NotificationService notificationService, AuditLogsService auditLogsService) {
        this.schedules = schedules;
        this.appointments = appointments;
        this.patients = patients;
        this.accessPolicy = accessPolicy;
        this.clock = clock;
        this.notificationService = notificationService;
        this.auditLogsService = auditLogsService;
    }

    public BookingService(ScheduleRepository schedules, AppointmentRepository appointments,
                          PatientRepository patients, AccessPolicy accessPolicy, Clock clock,
                          NotificationService notificationService) {
        this(schedules, appointments, patients, accessPolicy, clock, notificationService, null);
    }

    public BookingService(ScheduleRepository schedules, AppointmentRepository appointments,
                          PatientRepository patients, AccessPolicy accessPolicy, Clock clock) {
        this(schedules, appointments, patients, accessPolicy, clock, null, null);
    }

    public List<BookingAvailability> availability(UUID authUserId) {
        accessPolicy.requirePatient(authUserId);
        Instant now = clock.instant();
        List<Schedule> candidates = schedules.findBookingCandidates(SlotStatus.OPEN, LocalDate.ofInstant(now, clock.getZone()))
                .stream().filter(slot -> isBookable(slot, now)).toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        // One grouped query supplies the counts used both for display and for hiding full windows.
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : appointments.countOccupyingBySlotIds(candidates.stream().map(Schedule::getSlotId).toList())) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return candidates.stream()
                .filter(slot -> counts.getOrDefault(slot.getSlotId(), 0L) < slot.effectiveCapacity())
                .map(slot -> availabilityResponse(slot, counts.getOrDefault(slot.getSlotId(), 0L)))
                .toList();
    }

    @Transactional
    public BookingResponse book(UUID authUserId, UUID slotId, LocalTime appointmentTime) {
        Patient patient = requireProfile(authUserId);
        if (slotId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slotId is required");
        }
        if (appointmentTime != null && (appointmentTime.getSecond() != 0 || appointmentTime.getNano() != 0
                || appointmentTime.getMinute() % 5 != 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "appointmentTime must be on a 5-minute boundary");
        }
        Schedule slot = schedules.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));
        if (appointmentTime == null) appointmentTime = slot.getStartTime();
        // Read time only after acquiring the lock: a waiter may cross the slot start time.
        Instant now = clock.instant();
        long booked = appointments.countOccupyingBySlotId(slot.getSlotId());
        if (!isBookable(slot, now) || booked >= slot.effectiveCapacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot is no longer available for booking");
        }
        if (appointmentTime.isBefore(slot.getStartTime()) || !appointmentTime.isBefore(slot.getEndTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "appointmentTime must fall within the availability window");
        }
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setStaff(slot.getStaff());
        appointment.setSlot(slot);
        appointment.setAppointmentTime(appointmentTime);
        appointment.setAppointmentStatus(AppointmentStatus.BOOKED);
        appointment.setPriority(Priority.NORMAL);
        appointment.setCheckInTime(null);
        appointment.setBookedAt(LocalDateTime.ofInstant(now, clock.getZone()));
        appointments.saveAndFlush(appointment);
        // A window flips to BOOKED only once its capacity is reached, which hides it from availability.
        if (booked + 1 >= slot.effectiveCapacity()) {
            slot.setSlotStatus(SlotStatus.BOOKED);
            schedules.saveAndFlush(slot);
        }
        com.cliniq.backend.model.Notification staffNotification = new com.cliniq.backend.model.Notification();
        staffNotification.setAppointment(appointment);
        staffNotification.setMessage("A new appointment has been booked: " + patient.getPatientName());
        staffNotification.setType("NEW_BOOKING");
        staffNotification.setIsRead(false);
        staffNotification.setCreatedAt(LocalDateTime.now());
        if (notificationService != null) notificationService.createNotification(staffNotification);
        if (auditLogsService != null) {
            auditLogsService.record("BOOKING_CREATED", "Patient booked an appointment", "Appointment",
                    appointment.getAppointmentId(), null);
        }
        return response(appointment);
    }

    @Transactional
    public BookingResponse book(UUID authUserId, UUID slotId) {
        return book(authUserId, slotId, null);
    }

    public List<BookingResponse> list(UUID authUserId) {
        Patient patient = requireProfile(authUserId);
        return appointments.findBookingsByPatientId(patient.getPatientID()).stream().map(this::response).toList();
    }

    public BookingResponse get(UUID authUserId, UUID id) {
        Patient patient = requireProfile(authUserId);
        return appointments.findBookingByIdAndPatientId(id, patient.getPatientID()).map(this::response)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private Patient requireProfile(UUID authUserId) {
        accessPolicy.requirePatient(authUserId);
        return patients.findByAuthUserId(authUserId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.CONFLICT, "Complete your patient profile before accessing bookings"));
    }

    private boolean isBookable(Schedule slot, Instant now) {
        if (slot.getSlotStatus() != SlotStatus.OPEN || slot.getSlotDate() == null
                || slot.getStartTime() == null || slot.getEndTime() == null
                || !slot.getEndTime().isAfter(slot.getStartTime())) {
            return false;
        }
        Staff doctor = slot.getStaff();
        if (doctor == null || !Boolean.TRUE.equals(doctor.getActive())
                || doctor.getStaffRole() != StaffRole.DOCTOR || doctor.getDepartment() == null) {
            return false;
        }
        LocalDateTime start = slot.getSlotDate().atTime(slot.getStartTime());
        LocalDateTime end = slot.getSlotDate().atTime(slot.getEndTime());
        // Reject nonexistent/ambiguous local times rather than silently shifting a clinic schedule.
        var offsets = clock.getZone().getRules().getValidOffsets(start);
        return offsets.size() == 1 && clock.getZone().getRules().getValidOffsets(end).size() == 1
                && start.toInstant(offsets.getFirst()).isAfter(now);
    }

    private BookingAvailability availabilityResponse(Schedule slot, long bookedCount) {
        Staff doctor = slot.getStaff();
        var department = doctor.getDepartment();
        return new BookingAvailability(slot.getSlotId(), slot.getSlotDate(), slot.getStartTime(), slot.getEndTime(),
                slot.getSlotStatus(), new BookingAvailability.Doctor(doctor.getStaffId(), doctor.getFirstName(),
                doctor.getLastName(), doctor.getStaffRole(), doctor.getAvailabilityStatus(), true,
                new BookingAvailability.Department(department.getDepartmentId(), department.getName())),
                slot.effectiveCapacity(), bookedCount);
    }

    private BookingResponse response(Appointment appointment) {
        Schedule slot = appointment.getSlot();
        Staff doctor = appointment.getStaff();

        return new BookingResponse(
                appointment.getAppointmentId(), appointment.getAppointmentStatus(), slot.getSlotId(),
                slot.getSlotDate(), slot.getStartTime(), slot.getEndTime(), appointment.getAppointmentTime(),
                doctor.getDepartment() == null ? null : doctor.getDepartment().getName(),
                doctor.getFirstName() + " " + doctor.getLastName(), appointment.getBookedAt(),
                appointment.getCheckInTime(),
                appointment.getCalledTime(),
                appointment.getCompletedTime(),
                appointment.getPriority() != null ? appointment.getPriority().name() : null
        );
    }
}
