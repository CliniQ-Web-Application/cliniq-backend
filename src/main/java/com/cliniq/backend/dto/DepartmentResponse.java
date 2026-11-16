package com.cliniq.backend.dto;

import com.cliniq.backend.model.Department;

import java.util.UUID;

public record DepartmentResponse(UUID departmentId, String name, String operatingHours) {
    public static DepartmentResponse from(Department department) {
        if (department == null) return null;
        return new DepartmentResponse(
                department.getDepartmentId(),
                department.getName(),
                department.getOperatingHours()
        );
    }
}
