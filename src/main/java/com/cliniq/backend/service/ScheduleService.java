package com.cliniq.backend.service;

import com.cliniq.backend.model.Schedule;
import com.cliniq.backend.repository.ScheduleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ScheduleService {
    private final ScheduleRepository scheduleRepository;

    public ScheduleService(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    public List<Schedule> getAllSchedules() {
        return scheduleRepository.findAll();
    }

    public Schedule getScheduleById(UUID id) {
        return scheduleRepository.findById(id).orElseThrow(() -> new RuntimeException("Schedule not found with id: " + id));
    }

    public Schedule createSchedule(Schedule schedule) {
        return scheduleRepository.save(schedule);
    }

    public Schedule updateSchedule(UUID id, Schedule details) {
        Schedule schedule = getScheduleById(id);
        schedule.setSlotDate(details.getSlotDate());
        schedule.setStartTime(details.getStartTime());
        schedule.setEndTime(details.getEndTime());
        schedule.setSlotStatus(details.getSlotStatus());
        schedule.setStaff(details.getStaff());
        return scheduleRepository.save(schedule);
    }

    public void deleteSchedule(UUID id) {
        if (!scheduleRepository.existsById(id)) {
            throw new RuntimeException("Schedule not found with id: " + id);
        }
        scheduleRepository.deleteById(id);
    }
}