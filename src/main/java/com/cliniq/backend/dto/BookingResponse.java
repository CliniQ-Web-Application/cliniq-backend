package com.cliniq.backend.dto;

import com.cliniq.backend.enums.AppointmentStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record BookingResponse(UUID appointmentId, AppointmentStatus appointmentStatus, UUID slotId,
                              LocalDate slotDate, LocalTime startTime, LocalTime endTime,
                              LocalTime appointmentTime,
                              String departmentName, String doctorName, LocalDateTime bookedAt, LocalDateTime checkInTime,
                              LocalDateTime calledTime, LocalDateTime completedTime, String priority) {
    public BookingResponse(UUID appointmentId, AppointmentStatus appointmentStatus, UUID slotId,
                           LocalDate slotDate, LocalTime startTime, LocalTime endTime,
                           String departmentName, String doctorName, LocalDateTime bookedAt, LocalDateTime checkInTime,
                           LocalDateTime calledTime, LocalDateTime completedTime, String priority) {
        this(appointmentId, appointmentStatus, slotId, slotDate, startTime, endTime, null,
                departmentName, doctorName, bookedAt, null, null, null, null);
    }
}
