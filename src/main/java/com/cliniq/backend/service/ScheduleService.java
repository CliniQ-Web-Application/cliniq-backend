package com.cliniq.backend.service;

import com.cliniq.backend.model.Schedule;
import com.cliniq.backend.enums.SlotStatus;
import com.cliniq.backend.repository.AppointmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.cliniq.backend.repository.ScheduleRepository;
import com.cliniq.backend.repository.StaffRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

@Service
public class ScheduleService {
    private final ScheduleRepository scheduleRepository;

    private final AppointmentRepository appointmentRepository;
    private final StaffRepository staffRepository;

    public ScheduleService(ScheduleRepository scheduleRepository, AppointmentRepository appointmentRepository,
                           StaffRepository staffRepository) {
        this.scheduleRepository = scheduleRepository;
        this.appointmentRepository = appointmentRepository;
        this.staffRepository = staffRepository;
    }

    @Transactional
    public Schedule createForStaff(Jwt jwt, Schedule schedule) {
        UUID userId = AccessPolicy.subject(jwt);
        var staff = staffRepository.findByAuthUserId(userId)
                .filter(profile -> Boolean.TRUE.equals(profile.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "An active staff account is required"));
        // A window without an explicit capacity keeps the legacy one-appointment behaviour (null -> 1).
        if (schedule.getCapacity() != null && schedule.getCapacity() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Capacity must be at least 1");
        }
        schedule.setSlotId(null);
        schedule.setSlotStatus(SlotStatus.OPEN);
        schedule.setStaff(staff);
        return createSchedule(schedule);
    }

    public List<Schedule> getAllSchedules() {
        return scheduleRepository.findAll();
    }

    public Schedule getScheduleById(UUID id) {
        return scheduleRepository.findById(id).orElseThrow(() -> new RuntimeException("Schedule not found with id: " + id));
    }

    @Transactional
    public Schedule createSchedule(Schedule schedule) {
        if (schedule.getSlotId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slot ID must not be supplied");
        }
        rejectBookedStatus(schedule);
        return scheduleRepository.save(schedule);
    }

    @Transactional
    public Schedule updateSchedule(UUID id, Schedule details) {
        Schedule schedule = lockMutableSlot(id);
        rejectBookedStatus(details);
        schedule.setSlotDate(details.getSlotDate());
        schedule.setStartTime(details.getStartTime());
        schedule.setEndTime(details.getEndTime());
        schedule.setSlotStatus(details.getSlotStatus());
        schedule.setStaff(details.getStaff());
        return scheduleRepository.save(schedule);
    }

    @Transactional
    public void deleteSchedule(UUID id) {
        scheduleRepository.delete(lockMutableSlot(id));
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
}