package com.cliniq.backend.controller;

import com.cliniq.backend.dto.AppointmentResponse;
import com.cliniq.backend.dto.PageResponse;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.service.AppointmentService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AppointmentResponse>> getAll(
            @PageableDefault(size = 200, sort = "appointmentId") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(appointmentService.getAppointments(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(appointmentService.getAppointmentDto(id));
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> create(@RequestBody Appointment appointment) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appointmentService.createAppointmentDto(appointment));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AppointmentResponse> update(@PathVariable UUID id, @RequestBody Appointment appointment) {
        return ResponseEntity.ok(appointmentService.updateAppointmentDto(id, appointment));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        appointmentService.deleteAppointment(id);
        return ResponseEntity.noContent().build();
    }
}
