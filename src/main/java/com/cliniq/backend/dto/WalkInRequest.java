package com.cliniq.backend.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record WalkInRequest(
        @NotNull UUID patientId,
        @NotNull UUID departmentId
) {
}
