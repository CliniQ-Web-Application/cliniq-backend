package com.cliniq.backend.dto;

import com.cliniq.backend.enums.AvailabilityStatus;
import jakarta.validation.constraints.NotNull;

public record AvailabilityUpdateRequest(@NotNull AvailabilityStatus status) {
}
