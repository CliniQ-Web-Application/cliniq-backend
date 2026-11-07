package com.cliniq.backend.dto;

import com.cliniq.backend.enums.AppointmentStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record BookingResponse(UUID appointmentId, AppointmentStatus appointmentStatus, UUID slotId,
                              LocalDate slotDate, LocalTime startTime, LocalTime endTime,
                              String departmentName, String doctorName, LocalDateTime bookedAt) { }
