package com.cliniq.backend.dto;

import com.cliniq.backend.enums.StaffRole;
import com.cliniq.backend.model.Department;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StaffRegistrationRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        @NotNull UUID departmentId,
        @NotNull StaffRole staffRole,
        @NotBlank @Size(min = 6) String password
) {}

