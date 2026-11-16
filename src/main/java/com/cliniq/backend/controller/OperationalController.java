package com.cliniq.backend.controller;

import com.cliniq.backend.dto.AppointmentResponse;
import com.cliniq.backend.dto.PriorityRequest;
import com.cliniq.backend.dto.QueueResponse;
import com.cliniq.backend.dto.WalkInRequest;
import com.cliniq.backend.service.OperationalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class OperationalController {
    private final OperationalService operationalService;

    public OperationalController(OperationalService operationalService) {
        this.operationalService = operationalService;
    }

    @PostMapping("/appointments/{id}/check-in")
    public AppointmentResponse checkIn(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return operationalService.checkInDto(jwt, id);
    }

    @PostMapping("/appointments/walk-in")
    public AppointmentResponse walkIn(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WalkInRequest request) {
        return operationalService.registerWalkInDto(jwt, request);
    }

    @PostMapping("/appointments/{id}/complete")
    public AppointmentResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return operationalService.completeDto(jwt, id);
    }

    @PatchMapping("/appointments/{id}/cancel")
    public AppointmentResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return operationalService.cancelPatientDto(jwt, id);
    }

    @GetMapping("/appointments/{id}/queue-position")
    public QueueResponse queuePosition(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return operationalService.queuePositionDto(jwt, id);
    }

    @PostMapping("/queues/{id}/prioritise")
    public QueueResponse prioritise(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                    @Valid @RequestBody PriorityRequest request) {
        return operationalService.prioritiseDto(jwt, id, request);
    }

    @PostMapping("/queues/call-next")
    public AppointmentResponse callNext(@AuthenticationPrincipal Jwt jwt) {
        return operationalService.callNextDto(jwt);
    }

    @PostMapping("/appointments/{id}/no-show")
    public AppointmentResponse markNoShow(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return operationalService.markNoShowDto(jwt, id);
    }

    @PostMapping("/appointments/{id}/staff-cancel")
    public AppointmentResponse cancelByStaff(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return operationalService.cancelByStaffDto(jwt, id);
    }

    @PostMapping("/queues/broadcast-delay")
    public ResponseEntity<Void> broadcastDelay(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, String> body) {
        operationalService.broadcastDelay(jwt, body.get("message"));
        return ResponseEntity.ok().build();
    }
}
