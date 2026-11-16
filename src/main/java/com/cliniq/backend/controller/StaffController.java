package com.cliniq.backend.controller;

import com.cliniq.backend.dto.AvailabilityUpdateRequest;
import com.cliniq.backend.dto.PageResponse;
import com.cliniq.backend.dto.StaffRegistrationRequest;
import com.cliniq.backend.dto.StaffResponse;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.service.AccessPolicy;
import com.cliniq.backend.service.OperationalService;
import com.cliniq.backend.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
public class StaffController {
    private final StaffService staffService;
    private final OperationalService operationalService;
    private final AccessPolicy accessPolicy;

    public StaffController(StaffService staffService, OperationalService operationalService,
                           AccessPolicy accessPolicy) {
        this.staffService = staffService;
        this.operationalService = operationalService;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping
    public ResponseEntity<PageResponse<StaffResponse>> getAll(
            @PageableDefault(size = 200, sort = "staffId") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(staffService.getStaffPage(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StaffResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(staffService.getStaffDto(id));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerStaff(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StaffRegistrationRequest request) {

        accessPolicy.requireAdmin(jwt);
        Staff created = staffService.registerStaff(request, jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Staff account created successfully.",
                "staffId", created.getStaffId()));
    }

    @PatchMapping("/me/availability")
    public StaffResponse updateAvailability(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody AvailabilityUpdateRequest request) {
        return operationalService.updateAvailabilityDto(jwt, request);
    }

    @PutMapping("/{id}")
    public ResponseEntity<StaffResponse> update(@PathVariable UUID id, @RequestBody Staff staff,
                                                @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(staffService.updateStaffDto(id, staff, jwt));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        staffService.deleteStaff(id, jwt);
        return ResponseEntity.noContent().build();
    }
}
