package com.cliniq.backend.controller;

import com.cliniq.backend.model.Staff;
import com.cliniq.backend.service.StaffService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
public class StaffController {
    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public ResponseEntity<List<Staff>> getAll() {
        return ResponseEntity.ok(staffService.getAllStaff());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Staff> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(staffService.getStaffById(id));
    }

    @PostMapping
    public ResponseEntity<Staff> create(@RequestBody Staff staff) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.createStaff(staff));
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