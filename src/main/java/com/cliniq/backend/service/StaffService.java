
package com.cliniq.backend.service;

import com.cliniq.backend.dto.StaffRegistrationRequest;
import com.cliniq.backend.enums.AvailabilityStatus;
import com.cliniq.backend.enums.StaffRole;
import com.cliniq.backend.model.Department;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.DepartmentRepository;
import com.cliniq.backend.repository.StaffRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

@Service
public class StaffService {

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

    public void deleteStaff(UUID id, Jwt jwt) {
        if (!staffRepository.existsById(id)) { throw new RuntimeException("Staff not found with id: " + id);}

        staffRepository.deleteById(id);
        auditLogsService.record("STAFF_DELETED", "Staff account deleted", "Staff", id, jwt);
    }
}
