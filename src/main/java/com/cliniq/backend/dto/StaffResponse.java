package com.cliniq.backend.dto;

import com.cliniq.backend.enums.AvailabilityStatus;
import com.cliniq.backend.model.Staff;

import java.util.UUID;

/** Staff projection that never exposes the internal Supabase auth user id. */
public record StaffResponse(
        UUID staffId,
        String firstName,
        String lastName,
        String staffRole,
        String availabilityStatus,
        String email,
        boolean active,
        DepartmentResponse department
) {
    public static StaffResponse from(Staff staff) {
        if (staff == null) return null;
        AvailabilityStatus availability = staff.getAvailabilityStatus();
        return new StaffResponse(
                staff.getStaffId(),
                staff.getFirstName(),
                staff.getLastName(),
                staff.getStaffRole() == null ? null : staff.getStaffRole().name(),
                availability == null ? null : availability.name(),
                staff.getEmail(),
                Boolean.TRUE.equals(staff.getActive()),
                DepartmentResponse.from(staff.getDepartment())
        );
    }
}
