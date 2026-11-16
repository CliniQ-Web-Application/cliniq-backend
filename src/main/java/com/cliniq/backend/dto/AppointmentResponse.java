package com.cliniq.backend.dto;

import com.cliniq.backend.model.Appointment;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentResponse(
        UUID appointmentId,
        String appointmentStatus,
        String priority,
        LocalTime appointmentTime,
        LocalDateTime checkInTime,
        LocalDateTime calledTime,
        LocalDateTime completedTime,
        LocalDateTime bookedAt,
        LocalDateTime createdAt,
        PatientResponse patient,
        StaffResponse staff,
        ScheduleResponse slot
) {
    public static AppointmentResponse from(Appointment appointment) {
        if (appointment == null) return null;
        return new AppointmentResponse(
                appointment.getAppointmentId(),
                appointment.getAppointmentStatus() == null ? null : appointment.getAppointmentStatus().name(),
                appointment.getPriority() == null ? null : appointment.getPriority().name(),
                appointment.getAppointmentTime(),
                appointment.getCheckInTime(),
                appointment.getCalledTime(),
                appointment.getCompletedTime(),
                appointment.getBookedAt(),
                appointment.getCreatedAt(),
                PatientResponse.from(appointment.getPatient()),
                StaffResponse.from(appointment.getStaff()),
                ScheduleResponse.from(appointment.getSlot())
        );
    }
}
