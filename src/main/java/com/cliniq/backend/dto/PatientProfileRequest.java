package com.cliniq.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PatientProfileRequest(
        @NotBlank @Size(max = 255) String patientName,
        @NotBlank @Size(max = 255) String patientSurname,
        @NotBlank @Size(max = 255) String nationalID,
        @NotBlank @Size(max = 255) String phone) {
}
