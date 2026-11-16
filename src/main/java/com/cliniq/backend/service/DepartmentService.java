package com.cliniq.backend.service;

import com.cliniq.backend.dto.DepartmentResponse;
import com.cliniq.backend.model.Department;
import com.cliniq.backend.repository.DepartmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

@Service
public class DepartmentService {
    private final DepartmentRepository departmentRepository;
    private final AuditLogsService auditLogsService;

    public DepartmentService(DepartmentRepository departmentRepository, AuditLogsService auditLogsService) {
        this.departmentRepository = departmentRepository;
        this.auditLogsService = auditLogsService;
    }

    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    public Department getDepartmentById(UUID id) {
        return departmentRepository.findById(id).orElseThrow(() -> new RuntimeException("Department not found with id: " + id));
    }

    public Page<DepartmentResponse> getDepartments(Pageable pageable) {
        return departmentRepository.findAll(pageable).map(DepartmentResponse::from);
    }

    public DepartmentResponse getDepartmentDto(UUID id) {
        return DepartmentResponse.from(getDepartmentById(id));
    }

    public DepartmentResponse createDepartmentDto(Department department, Jwt jwt) {
        return DepartmentResponse.from(createDepartment(department, jwt));
    }

    public Department createDepartment(Department department, Jwt jwt) {
        Department saved = departmentRepository.save(department);
        auditLogsService.record("DEPARTMENT_CREATED", "Department created", "Department", saved.getDepartmentId(), jwt);
        return saved;
    }

    public DepartmentResponse updateDepartmentDto(UUID id, Department details, Jwt jwt) {
        return DepartmentResponse.from(updateDepartment(id, details, jwt));
    }

    public Department updateDepartment(UUID id, Department details, Jwt jwt) {
        Department department = getDepartmentById(id);
        department.setName(details.getName());
        department.setOperatingHours(details.getOperatingHours());
        Department saved = departmentRepository.save(department);
        auditLogsService.record("DEPARTMENT_UPDATED", "Department updated", "Department", saved.getDepartmentId(), jwt);
        return saved;
    }

    public void deleteDepartment(UUID id, Jwt jwt) {
        if (!departmentRepository.existsById(id)) {
            throw new RuntimeException("Department not found with id: " + id);
        }
        departmentRepository.deleteById(id);
        auditLogsService.record("DEPARTMENT_DELETED", "Department deleted", "Department", id, jwt);
    }
}