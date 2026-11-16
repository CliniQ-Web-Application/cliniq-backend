package com.cliniq.backend.service;

import com.cliniq.backend.dto.AdminScheduleRequest;
import com.cliniq.backend.dto.AdminScheduleResponse;
import com.cliniq.backend.enums.SlotStatus;
import com.cliniq.backend.enums.StaffRole;
import com.cliniq.backend.model.Schedule;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.AppointmentRepository;
import com.cliniq.backend.repository.ScheduleRepository;
import com.cliniq.backend.repository.StaffRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class ScheduleService {
    private final ScheduleRepository scheduleRepository;
    private final AppointmentRepository appointmentRepository;
    private final StaffRepository staffRepository;
    private final AuditLogsService auditLogsService;
    private final AccessPolicy accessPolicy;
    private final Clock clock;

    @Autowired
    public ScheduleService(ScheduleRepository schedules, AppointmentRepository appointments, StaffRepository staff,
                           AuditLogsService audit, AccessPolicy accessPolicy, Clock clock) {
        this.scheduleRepository = schedules;
        this.appointmentRepository = appointments;
        this.staffRepository = staff;
        this.auditLogsService = audit;
        this.accessPolicy = accessPolicy;
        this.clock = clock;
    }

    public ScheduleService(ScheduleRepository schedules, AppointmentRepository appointments,
                           StaffRepository staff, AuditLogsService audit) {
        this(schedules, appointments, staff, audit, new AccessPolicy(staff), Clock.system(ZoneId.of("Africa/Gaborone")));
    }

    public ScheduleService(ScheduleRepository schedules, AppointmentRepository appointments, StaffRepository staff) {
        this(schedules, appointments, staff, null);
    }

    public ScheduleService(ScheduleRepository schedules, AppointmentRepository appointments) {
        this(schedules, appointments, null, null);
    }

    @Transactional
    public Schedule createForStaff(Jwt jwt, Schedule schedule) {
        Staff owner = requireActor(jwt);
        if (owner.getStaffRole() != StaffRole.DOCTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only doctors can create their own schedules");
        }
        requireDoctor(owner);
        if (schedule == null) throw badRequest("Schedule is required");
        schedule.setSlotId(null);
        schedule.setSlotStatus(SlotStatus.OPEN);
        schedule.setStaff(owner);
        Schedule saved = createSchedule(schedule);
        audit("SCHEDULE_CREATED", saved.getSlotId(), jwt);
        return saved;
    }

    public List<Schedule> getAllSchedules() { return scheduleRepository.findAll(); }

    public Schedule getScheduleById(UUID id) {
        return scheduleRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));
    }

    @Transactional
    public Schedule createSchedule(Schedule schedule) {
        if (schedule.getSlotId() != null) throw badRequest("Slot ID must not be supplied");
        rejectBookedStatus(schedule);
        requireDoctor(schedule.getStaff());
        validateWindow(schedule);
        return scheduleRepository.save(schedule);
    }

    @Transactional
    public Schedule updateSchedule(UUID id, Schedule details, Jwt jwt) {
        Staff actor = requireActor(jwt);
        Schedule slot = lockMutableSlot(id);
        requireOwnerOrAdmin(actor, slot);
        if (details == null) throw badRequest("Schedule is required");
        // Generic HTTP edits cannot move a window to another doctor; use the admin DTO API.
        if (details.getStaff() != null && !slot.getStaff().getStaffId().equals(details.getStaff().getStaffId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Use the admin schedule API to reassign a doctor");
        }
        details.setStaff(slot.getStaff());
        if (details.getCapacity() == null) details.setCapacity(slot.getCapacity());
        rejectBookedStatus(details);
        requireDoctor(details.getStaff());
        validateWindow(details);
        apply(slot, details);
        Schedule saved = scheduleRepository.save(slot);
        audit("SCHEDULE_UPDATED", id, jwt);
        return saved;
    }

    @Transactional
    public void deleteSchedule(UUID id, Jwt jwt) {
        Staff actor = requireActor(jwt);
        Schedule slot = lockMutableSlot(id);
        requireOwnerOrAdmin(actor, slot);
        scheduleRepository.delete(slot);
        audit("SCHEDULE_DELETED", id, jwt);
    }

    // Retained for legacy in-process callers; HTTP mutations always use the JWT overloads.
    @Transactional
    public Schedule updateSchedule(UUID id, Schedule details) {
        Schedule slot = lockMutableSlot(id);
        rejectBookedStatus(details);
        apply(slot, details);
        return scheduleRepository.save(slot);
    }

    @Transactional
    public void deleteSchedule(UUID id) { scheduleRepository.delete(lockMutableSlot(id)); }

    @Transactional(readOnly = true)
    public List<AdminScheduleResponse> listAdmin(Jwt jwt) {
        accessPolicy.requireAdmin(jwt);
        return scheduleRepository.findAllForAdmin().stream().map(AdminScheduleResponse::from).toList();
    }

    @Transactional
    public AdminScheduleResponse createAdmin(Jwt jwt, AdminScheduleRequest request) {
        accessPolicy.requireAdmin(jwt);
        Schedule slot = adminDetails(request);
        Schedule saved = scheduleRepository.save(slot);
        audit("SCHEDULE_CREATED", saved.getSlotId(), jwt);
        return AdminScheduleResponse.from(saved);
    }

    @Transactional
    public AdminScheduleResponse updateAdmin(Jwt jwt, UUID id, AdminScheduleRequest request) {
        accessPolicy.requireAdmin(jwt);
        Schedule slot = lockMutableSlot(id);
        apply(slot, adminDetails(request));
        Schedule saved = scheduleRepository.save(slot);
        audit("SCHEDULE_UPDATED", id, jwt);
        return AdminScheduleResponse.from(saved);
    }

    @Transactional
    public void deleteAdmin(Jwt jwt, UUID id) {
        accessPolicy.requireAdmin(jwt);
        scheduleRepository.delete(lockMutableSlot(id));
        audit("SCHEDULE_DELETED", id, jwt);
    }

    private Schedule adminDetails(AdminScheduleRequest request) {
        if (request == null || request.staffId() == null) throw badRequest("staffId is required");
        int capacity = wholeNumber(request.capacity(), 10000, "capacity");
        Staff doctor = staffRepository.findById(request.staffId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
        requireDoctor(doctor);
        Schedule slot = new Schedule();
        slot.setStaff(doctor);
        slot.setSlotDate(request.slotDate());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setCapacity(capacity);
        slot.setSlotStatus(request.slotStatus() == null ? SlotStatus.OPEN : request.slotStatus());
        rejectBookedStatus(slot);
        validateWindow(slot);
        return slot;
    }

    static int wholeNumber(Number number, int maximum, String field) {
        // Number preserves JSON's integer vs decimal token, avoiding Jackson's Integer truncation.
        if (!(number instanceof Integer || number instanceof Long) || number.longValue() < 1 || number.longValue() > maximum) {
            throw badRequest(field + " must be a whole number between 1 and " + maximum);
        }
        return number.intValue();
    }

    private void validateWindow(Schedule slot) {
        if (slot.getCapacity() != null) wholeNumber(slot.getCapacity(), 10000, "capacity");
        if (slot.getSlotDate() == null || slot.getStartTime() == null || slot.getEndTime() == null
                || !slot.getEndTime().isAfter(slot.getStartTime())) {
            throw badRequest("A date and same-day end time after start time are required");
        }
        var start = slot.getSlotDate().atTime(slot.getStartTime());
        var end = slot.getSlotDate().atTime(slot.getEndTime());
        var rules = clock.getZone().getRules();
        var offsets = rules.getValidOffsets(start);
        if (offsets.size() != 1 || rules.getValidOffsets(end).size() != 1
                || !start.toInstant(offsets.getFirst()).isAfter(clock.instant())) {
            throw badRequest("Schedule must start in the future at an unambiguous clinic-local time");
        }
        if (slot.getSlotStatus() == null) slot.setSlotStatus(SlotStatus.OPEN);
    }

    private Staff requireActor(Jwt jwt) {
        return staffRepository.findByAuthUserId(AccessPolicy.subject(jwt))
                .filter(profile -> Boolean.TRUE.equals(profile.getActive()))
                .filter(profile -> profile.getStaffRole() == StaffRole.ADMIN || profile.getStaffRole() == StaffRole.DOCTOR)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "An active doctor or administrator is required"));
    }

    private void requireOwnerOrAdmin(Staff actor, Schedule slot) {
        if (actor.getStaffRole() != StaffRole.ADMIN && (slot.getStaff() == null
                || !actor.getStaffId().equals(slot.getStaff().getStaffId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the doctor owner or an administrator may change this schedule");
        }
    }

    private void requireDoctor(Staff doctor) {
        if (doctor == null || !Boolean.TRUE.equals(doctor.getActive()) || doctor.getStaffRole() != StaffRole.DOCTOR
                || doctor.getDepartment() == null) {
            throw badRequest("Schedules require an active DOCTOR with a department");
        }
    }

    private void apply(Schedule slot, Schedule details) {
        slot.setSlotDate(details.getSlotDate());
        slot.setStartTime(details.getStartTime());
        slot.setEndTime(details.getEndTime());
        slot.setSlotStatus(details.getSlotStatus());
        slot.setCapacity(details.getCapacity());
        slot.setStaff(details.getStaff());
    }

    private Schedule lockMutableSlot(UUID id) {
        Schedule slot = scheduleRepository.findByIdForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));
        if (slot.getSlotStatus() == SlotStatus.BOOKED || appointmentRepository.existsBySlot_SlotId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserved slots cannot be changed or deleted");
        }
        return slot;
    }

    private void rejectBookedStatus(Schedule slot) {
        if (slot.getSlotStatus() == SlotStatus.BOOKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Use the booking API to reserve a slot");
        }
    }

    private void audit(String action, UUID id, Jwt jwt) {
        if (auditLogsService != null) auditLogsService.record(action, "Doctor availability changed", "Schedule", id, jwt);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
