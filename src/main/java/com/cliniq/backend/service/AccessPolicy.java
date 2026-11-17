package com.cliniq.backend.service;

import com.cliniq.backend.enums.StaffRole;
import com.cliniq.backend.repository.StaffRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AccessPolicy {
    private final StaffRepository staff;

    public AccessPolicy(StaffRepository staff) {
        this.staff = staff;
    }

    public void requirePatient(UUID authUserId) {
        // Inactive staff must not fall back to patient privileges.
        if (authUserId == null || staff.findByAuthUserId(authUserId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This operation is for patient accounts only");
        }
    }

    public boolean isStaff(Authentication authentication, boolean adminOnly) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        UUID userId;
        try {
            userId = subject(jwt);
        } catch (ResponseStatusException invalidSubject) {
            return false;
        }
        return staff.findByAuthUserId(userId)
                .filter(profile -> Boolean.TRUE.equals(profile.getActive()))
                .filter(profile -> profile.getStaffRole() != null)
                .filter(profile -> !adminOnly || profile.getStaffRole() == StaffRole.ADMIN)
                .isPresent();
    }

    public boolean isDoctorOrAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) return false;
        UUID userId;
        try {
            userId = subject(jwt);
        } catch (ResponseStatusException invalidSubject) {
            return false;
        }
        return staff.findByAuthUserId(userId)
                .filter(profile -> Boolean.TRUE.equals(profile.getActive()))
                .filter(profile -> profile.getStaffRole() == StaffRole.DOCTOR || profile.getStaffRole() == StaffRole.ADMIN)
                .isPresent();
    }

    public UUID requireAdmin(Jwt jwt) {
        UUID userId = subject(jwt);
        staff.findByAuthUserId(userId)
                .filter(profile -> Boolean.TRUE.equals(profile.getActive()))
                .filter(profile -> profile.getStaffRole() == StaffRole.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Only active administrators may perform this operation"));
        return userId;
    }

    public static UUID subject(Jwt jwt) {
        String sub = jwt == null ? null : jwt.getSubject();
        if (sub == null || !sub.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid authenticated user is required");
        }
        return UUID.fromString(sub);
    }
}
