package com.cliniq.backend.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.time.LocalTime;
import java.util.UUID;

public class BookingRequest {
    private UUID slotId;
    private LocalTime appointmentTime;

    public UUID getSlotId() { return slotId; }
    public void setSlotId(UUID slotId) { this.slotId = slotId; }
    public LocalTime getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(LocalTime appointmentTime) { this.appointmentTime = appointmentTime; }

    // Enforce the narrow contract even when global unknown-property handling is permissive.
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Only slotId is accepted");
    }
}
