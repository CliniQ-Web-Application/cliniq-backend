package com.cliniq.backend.controller;

import com.cliniq.backend.dto.DepartmentResponse;
import com.cliniq.backend.dto.PageResponse;
import com.cliniq.backend.model.Department;
import com.cliniq.backend.service.DepartmentService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {
    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<DepartmentResponse>> getAll(
            @PageableDefault(size = 200, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(departmentService.getDepartments(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepartmentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(departmentService.getDepartmentDto(id));
    }

    @PostMapping
    public ResponseEntity<DepartmentResponse> create(@RequestBody Department department,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(departmentService.createDepartmentDto(department, jwt));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentResponse> update(@PathVariable UUID id,
                                                     @RequestBody Department department,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(departmentService.updateDepartmentDto(id, department, jwt));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        departmentService.deleteDepartment(id, jwt);
        return ResponseEntity.noContent().build();
    }
}
