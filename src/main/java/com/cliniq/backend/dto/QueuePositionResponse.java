package com.cliniq.backend.dto;

import com.cliniq.backend.enums.AppointmentStatus;

import java.util.UUID;

public record QueuePositionResponse(UUID appointmentId, Integer queuePosition,
                                    AppointmentStatus queueStatus, Integer estimatedWaitMinutes) {
}
