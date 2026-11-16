package com.cliniq.backend.dto;

import com.cliniq.backend.model.Schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ScheduleResponse(
        UUID slotId,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        String slotStatus,
        Integer capacity,
        StaffResponse staff
) {
    public static ScheduleResponse from(Schedule schedule) {
        if (schedule == null) return null;
        return new ScheduleResponse(
                schedule.getSlotId(),
                schedule.getSlotDate(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getSlotStatus() == null ? null : schedule.getSlotStatus().name(),
                schedule.getCapacity(),
                StaffResponse.from(schedule.getStaff())
        );
    }
}
