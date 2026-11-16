package com.cliniq.backend.dto;

import com.cliniq.backend.enums.SlotStatus;
import com.cliniq.backend.model.Schedule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AdminScheduleResponse(UUID slotId, LocalDate slotDate, LocalTime startTime,
                                    LocalTime endTime, SlotStatus slotStatus, int capacity,
                                    BookingAvailability.Doctor staff) {
    public static AdminScheduleResponse from(Schedule slot) {
        var doctor = slot.getStaff();
        var department = doctor == null ? null : doctor.getDepartment();
        return new AdminScheduleResponse(slot.getSlotId(), slot.getSlotDate(), slot.getStartTime(),
                slot.getEndTime(), slot.getSlotStatus(), slot.effectiveCapacity(), doctor == null ? null :
                new BookingAvailability.Doctor(doctor.getStaffId(), doctor.getFirstName(), doctor.getLastName(),
                        doctor.getStaffRole(), doctor.getAvailabilityStatus(), Boolean.TRUE.equals(doctor.getActive()),
                        department == null ? null : new BookingAvailability.Department(department.getDepartmentId(), department.getName())));
    }
}
