package com.cliniq.backend.dto;

import com.cliniq.backend.enums.SlotStatus;
import com.cliniq.backend.enums.AvailabilityStatus;
import com.cliniq.backend.enums.StaffRole;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record BookingAvailability(UUID slotId, LocalDate slotDate, LocalTime startTime,
                                  LocalTime endTime, SlotStatus slotStatus, Doctor staff,
                                  int capacity, long bookedCount) {
    public record Doctor(UUID staffId, String firstName, String lastName, StaffRole staffRole,
                         AvailabilityStatus availabilityStatus, boolean active, Department department) { }
    public record Department(UUID departmentId, String name) { }
}
