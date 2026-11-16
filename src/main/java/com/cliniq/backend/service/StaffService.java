
package com.cliniq.backend.service;

import com.cliniq.backend.dto.StaffRegistrationRequest;
import com.cliniq.backend.dto.StaffResponse;
import com.cliniq.backend.enums.AvailabilityStatus;
import com.cliniq.backend.enums.StaffRole;
import com.cliniq.backend.model.Department;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.DepartmentRepository;
import com.cliniq.backend.repository.StaffRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

@Service
public class StaffService {

    private static final Logger log = LoggerFactory.getLogger(StaffService.class);

    private final StaffRepository staffRepository;
    private final DepartmentRepository departmentRepository;
    private final SupabaseAdminService supabaseAdminService;
    private final AuditLogsService auditLogsService;

    public StaffService(
            StaffRepository staffRepository,
            DepartmentRepository departmentRepository,
            SupabaseAdminService supabaseAdminService, AuditLogsService auditLogsService) {
        this.staffRepository = staffRepository;
        this.departmentRepository = departmentRepository;
        this.supabaseAdminService = supabaseAdminService;
        this.auditLogsService = auditLogsService;
    }

    public List<Staff> getAllStaff() {
        return staffRepository.findAll();
    }

    public Staff getStaffById(UUID id) {
        return staffRepository.findById(id).orElseThrow(() -> new RuntimeException("Staff member not found with id: " + id));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Page<StaffResponse> getStaffPage(Pageable pageable) {
        return staffRepository.findAllForList(pageable).map(StaffResponse::from);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public StaffResponse getStaffDto(UUID id) {
        return StaffResponse.from(getStaffById(id));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public StaffResponse getStaffDtoByAuthUser(UUID authUserId) {
        return StaffResponse.from(staffRepository.findByAuthUserId(authUserId).orElse(null));
    }

    public Staff createStaff(Staff staff) {
        return staffRepository.save(staff);
    }

    @Transactional
    public Staff registerStaff(StaffRegistrationRequest request, Jwt jwt) {
        String email = request.email().trim().toLowerCase();

        if (staffRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException(
                    "A staff profile already exists for this email.");
        }

        Department department = departmentRepository
                .findById(request.departmentId())
                .orElseThrow(() -> new IllegalArgumentException("Department not found."));

        String authUserId = supabaseAdminService.createUser( email, request.password());

        Staff staff = new Staff();
        staff.setAuthUserId(UUID.fromString(authUserId));
        staff.setFirstName(request.firstName().trim());
        staff.setLastName(request.lastName().trim());
        staff.setEmail(email);
        staff.setDepartment(department);
        staff.setStaffRole(request.staffRole());
        staff.setActive(true);

        staff.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);

        Staff saved = staffRepository.save(staff);
        auditLogsService.record("STAFF_REGISTERED", "Staff account registered", "Staff", saved.getStaffId(), jwt);
        return saved;
    }

    public Staff updateStaff(UUID id, Staff staffDetails, Jwt jwt) {
        Staff staff = getStaffById(id);
        staff.setFirstName(staffDetails.getFirstName());
        staff.setLastName(staffDetails.getLastName());
        staff.setStaffRole(staffDetails.getStaffRole());
        staff.setEmail(staffDetails.getEmail());
        staff.setActive(staffDetails.getActive());
        staff.setDepartment(staffDetails.getDepartment());
        staff.setAvailabilityStatus(staffDetails.getAvailabilityStatus());

        Staff saved = staffRepository.save(staff);
        auditLogsService.record("STAFF_UPDATED", "Staff account updated", "Staff", saved.getStaffId(), jwt);
        return saved;
    }

    @Transactional
    public StaffResponse updateStaffDto(UUID id, Staff details, Jwt jwt) {
        return StaffResponse.from(updateStaff(id, details, jwt));
    }

    /**
     * Soft-deletes a staff member. The row is retained so audit logs, appointments
     * and schedules keep their foreign-key target; the account is deactivated in
     * Supabase Auth as well. Repeat calls are safe (idempotent).
     */
    @Transactional
    public void deleteStaff(UUID id, Jwt jwt) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Staff not found with id: " + id));

        if (!Boolean.TRUE.equals(staff.getActive())) {
            return;
        }

        UUID authUserId = staff.getAuthUserId();
        staff.setActive(false);
        staff.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);

        // Release the unique email so the address can be registered again later.
        String tombstone = "deleted-" + id + "-";
        String retiredEmail = tombstone + staff.getEmail();
        staff.setEmail(retiredEmail.substring(0, Math.min(retiredEmail.length(), 100)));
        staffRepository.save(staff);

        if (authUserId != null) {
            try {
                supabaseAdminService.deactivateUser(authUserId);
            } catch (RuntimeException error) {
                // The local record is already safely deactivated; do not fail the request
                // because the external identity provider is temporarily unavailable.
                log.warn("Could not deactivate Supabase Auth user {} for staff {}", authUserId, id, error);
            }
        }

        auditLogsService.record("STAFF_DEACTIVATED", "Staff account deactivated (soft delete)", "Staff", id, jwt);
    }
}
