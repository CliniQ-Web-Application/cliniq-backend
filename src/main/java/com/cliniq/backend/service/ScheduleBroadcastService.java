package com.cliniq.backend.service;

import com.cliniq.backend.dto.ScheduleBroadcastRequest;
import com.cliniq.backend.dto.ScheduleBroadcastResponse;
import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.enums.StaffRole;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Notification;
import com.cliniq.backend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ScheduleBroadcastService {
    private final ScheduleRepository schedules;
    private final AppointmentRepository appointments;
    private final StaffRepository staff;
    private final NotificationRepository notifications;
    private final AuditLogsService audit;
    private final Clock clock;

    public ScheduleBroadcastService(ScheduleRepository schedules, AppointmentRepository appointments,
                                    StaffRepository staff, NotificationRepository notifications,
                                    AuditLogsService audit, Clock clock) {
        this.schedules = schedules;
        this.appointments = appointments;
        this.staff = staff;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ScheduleBroadcastResponse broadcast(Jwt jwt, UUID id, ScheduleBroadcastRequest request) {
        var actor = staff.findByAuthUserId(AccessPolicy.subject(jwt))
                .filter(profile -> Boolean.TRUE.equals(profile.getActive()))
                .filter(profile -> profile.getStaffRole() == StaffRole.DOCTOR || profile.getStaffRole() == StaffRole.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "An active doctor or administrator is required"));
        if (request == null || request.type() == null || request.message() == null
                || request.message().isBlank() || request.message().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type and a nonblank message of at most 500 characters are required");
        }
        Integer delay = request.type() == ScheduleBroadcastRequest.Type.DELAY
                ? ScheduleService.wholeNumber(request.delayMinutes(), 1440, "delayMinutes") : null;
        if (request.type() == ScheduleBroadcastRequest.Type.ABSENCE && request.delayMinutes() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ABSENCE must not include delayMinutes");
        }
        var slot = schedules.findByIdForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));
        var doctor = slot.getStaff();
        if (actor.getStaffRole() != StaffRole.ADMIN && (doctor == null
                || !actor.getStaffId().equals(doctor.getStaffId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the doctor owner or an administrator may broadcast");
        }
        String message = "Dr " + (doctor == null ? "(unassigned)" : doctor.getFirstName() + " " + doctor.getLastName())
                + " — " + slot.getSlotDate() + " " + slot.getStartTime() + "–" + slot.getEndTime() + ": "
                + (delay == null ? "Absence. " : "Delayed by " + delay + " minutes. ") + request.message().strip();
        // One patient-linked notification per distinct recipient; no recipient list leaves this service.
        Map<UUID, Appointment> recipients = new LinkedHashMap<>();
        for (Appointment appointment : appointments.findBroadcastRecipients(id,
                List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKEDIN, AppointmentStatus.WAITING))) {
            if (appointment.getPatient() != null && appointment.getPatient().getPatientID() != null) {
                recipients.putIfAbsent(appointment.getPatient().getPatientID(), appointment);
            }
        }
        List<Notification> messages = new ArrayList<>();
        for (Appointment appointment : recipients.values()) {
            Notification notification = new Notification();
            notification.setPatient(appointment.getPatient());
            notification.setAppointment(appointment);
            notification.setType("DOCTOR_" + request.type().name());
            notification.setMessage(message);
            notification.setIsRead(false);
            notification.setCreatedAt(LocalDateTime.now(clock));
            messages.add(notification);
        }
        notifications.saveAll(messages);
        audit.record("SCHEDULE_BROADCAST", request.type() + ": " + messages.size() + " recipients", "Schedule", id, jwt);
        return new ScheduleBroadcastResponse(messages.size());
    }
}
