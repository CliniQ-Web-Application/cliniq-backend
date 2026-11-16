package com.cliniq.backend.controller;

import com.cliniq.backend.dto.PriorityRequest;
import com.cliniq.backend.dto.QueueEntryResponse;
import com.cliniq.backend.dto.StaffAppointmentResponse;
import com.cliniq.backend.dto.WalkInRequest;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.service.OperationalService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class OperationalController {
    private final OperationalService operationalService;

    public OperationalController(OperationalService operationalService) {
        this.operationalService = operationalService;
    }

    @GetMapping("/staff/me/appointments")
    public List<StaffAppointmentResponse> staffAppointments(@AuthenticationPrincipal Jwt jwt) {
        return operationalService.getStaffAppointments(jwt);
    }

    @GetMapping("/queues/department")
    public List<QueueEntryResponse> departmentQueue(@AuthenticationPrincipal Jwt jwt) {
        return operationalService.getDepartmentQueue(jwt);
    }

    @PostMapping("/appointments/{id}/check-in")
    public QueueEntryResponse checkIn(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return operationalService.checkIn(jwt, id);
    }

    @PostMapping("/appointments/walk-in")
    public QueueEntryResponse walkIn(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WalkInRequest request) {
        return operationalService.registerWalkIn(jwt, request);
    }

    @PostMapping("/appointments/{id}/complete")
    public StaffAppointmentResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return operationalService.complete(jwt, id);
    }

    @PostMapping("/appointments/{id}/no-show")
    public StaffAppointmentResponse noShow(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return operationalService.markNoShow(jwt, id);
    }

    @PatchMapping("/appointments/{id}/cancel")
    public Appointment cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return operationalService.cancelPatient(jwt, id);
    }

    @GetMapping("/appointments/{id}/queue-position")
    public QueueEntryResponse queuePosition(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return operationalService.queuePosition(jwt, id);
    }

    @PostMapping("/queues/{id}/prioritise")
    public QueueEntryResponse prioritise(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @Valid @RequestBody PriorityRequest request) {
        return operationalService.prioritise(jwt, id, request);
    }

    @PostMapping("/queues/call-next")
    public QueueEntryResponse callNext(@AuthenticationPrincipal Jwt jwt) {
        return operationalService.callNext(jwt);
    }
}