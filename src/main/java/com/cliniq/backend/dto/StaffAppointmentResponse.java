package com.cliniq.backend.dto;

import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.enums.Priority;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record StaffAppointmentResponse(
        UUID appointmentId,
        AppointmentStatus appointmentStatus,
        Priority priority,
        UUID patientId,
        String patientName,
        String patientSurname,
        String nationalID,
        String phone,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        LocalDateTime createdAt,
        LocalDateTime bookedAt,
        LocalDateTime checkInTime,
        LocalDateTime calledTime,
        LocalDateTime completedTime,
        UUID queueId,
        Integer queuePosition
) {}