package com.cliniq.backend.dto;

import com.cliniq.backend.model.Notification;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID notificationId,
        String type,
        String message,
        boolean isRead,
        LocalDateTime createdAt,
        UUID appointmentId
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getNotificationId(),
                n.getType(),
                n.getMessage(),
                Boolean.TRUE.equals(n.getIsRead()),
                n.getCreatedAt(),
                n.getAppointment() == null ? null : n.getAppointment().getAppointmentId());
    }
}
