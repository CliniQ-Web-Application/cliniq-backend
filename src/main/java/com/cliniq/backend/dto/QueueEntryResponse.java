package com.cliniq.backend.dto;

import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.enums.Priority;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record QueueEntryResponse(
        UUID queueId,
        int position,
        int estimatedWaitMinutes,
        UUID appointmentId,
        AppointmentStatus appointmentStatus,
        Priority priority,
        UUID patientId,
        String patientName,
        String patientSurname,
        String departmentName,
        LocalDateTime createdAt,
        LocalDateTime checkInTime,
        LocalDateTime calledTime,
        LocalTime queueTimeStamp
) {}