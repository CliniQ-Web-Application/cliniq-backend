package com.cliniq.backend.controller;

import com.cliniq.backend.dto.AdminAppointment;
import com.cliniq.backend.dto.AdminAppointmentPage;
import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.service.AdminAppointmentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/appointments")
public class AdminAppointmentController {
    private final AdminAppointmentService appointments;

    public AdminAppointmentController(AdminAppointmentService appointments) { this.appointments = appointments; }

    @GetMapping
    public AdminAppointmentPage list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) UUID departmentId, @RequestParam(required = false) UUID doctorId,
            @RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return appointments.list(jwt, from, to, status, departmentId, doctorId, search, page, size);
    }

    @GetMapping("/{id}")
    public AdminAppointment get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return appointments.get(jwt, id); }

    @PatchMapping("/{id}/cancel")
    public AdminAppointment cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return appointments.cancel(jwt, id); }
}
