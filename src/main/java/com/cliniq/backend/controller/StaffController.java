package com.cliniq.backend.controller;

import com.cliniq.backend.dto.StaffRegistrationRequest;
import com.cliniq.backend.enums.StaffRole;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.StaffRepository;
import com.cliniq.backend.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.cliniq.backend.dto.AvailabilityUpdateRequest;
import com.cliniq.backend.service.OperationalService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
public class StaffController {
    private final StaffService staffService;
    private final StaffRepository staffRepository;
    private final OperationalService operationalService;

    public StaffController(StaffService staffService, StaffRepository staffRepository, OperationalService operationalService) {
        this.staffService = staffService;
        this.staffRepository = staffRepository;
        this.operationalService = operationalService;
    }

    @GetMapping
    public ResponseEntity<List<Staff>> getAll() {
        return ResponseEntity.ok(staffService.getAllStaff());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Staff> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(staffService.getStaffById(id));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerStaff(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StaffRegistrationRequest request) {

        UUID authUserId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));

        Staff currentUser = staffRepository.findByAuthUserId(authUserId).orElse(null);

        assert currentUser != null;
        if (currentUser.getStaffRole() != StaffRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only administrators can register staff."));
        }

        Staff created = staffService.registerStaff(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Staff account created successfully.", "staffId", created.getStaffId()));
    }

    @PatchMapping("/me/availability")
    public Staff updateAvailability(@AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody AvailabilityUpdateRequest request) {
        return operationalService.updateAvailability(jwt, request);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Staff> update(@PathVariable UUID id, @RequestBody Staff staff) {
        return ResponseEntity.ok(staffService.updateStaff(id, staff));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        staffService.deleteStaff(id);
        return ResponseEntity.noContent().build();
    }
}