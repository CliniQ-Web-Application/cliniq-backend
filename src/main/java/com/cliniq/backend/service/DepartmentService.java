package com.cliniq.backend.service;

import com.cliniq.backend.model.Department;
import com.cliniq.backend.repository.DepartmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DepartmentService {
    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    public Department getDepartmentById(UUID id) {
        return departmentRepository.findById(id).orElseThrow(() -> new RuntimeException("Department not found with id: " + id));
    }

    public Department createDepartment(Department department) {
        return departmentRepository.save(department);
    }

    public Department updateDepartment(UUID id, Department details) {
        Department department = getDepartmentById(id);
        department.setName(details.getName());
        department.setOperatingHours(details.getOperatingHours());
        return departmentRepository.save(department);
    }

    public void deleteDepartment(UUID id) {
        if (!departmentRepository.existsById(id)) {
            throw new RuntimeException("Department not found with id: " + id);
        }
        departmentRepository.deleteById(id);
    }
}