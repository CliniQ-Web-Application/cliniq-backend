package com.cliniq.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record PriorityRequest(@NotBlank String reason) {
}
