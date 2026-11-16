package com.cliniq.backend.controller;

import com.cliniq.backend.dto.PriorityRequest;
import com.cliniq.backend.dto.QueuePositionResponse;
import com.cliniq.backend.dto.WalkInRequest;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Queue;
import com.cliniq.backend.service.OperationalService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class OperationalController {
    private final OperationalService operationalService;

    public OperationalController(OperationalService operationalService) {
        this.operationalService = operationalService;
    }

    @PostMapping("/appointments/{id}/check-in")
    public Appointment checkIn(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return operationalService.checkIn(jwt, id);
    }

    @PostMapping("/appointments/walk-in")
    public Appointment walkIn(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WalkInRequest request) {
        return operationalService.registerWalkIn(jwt, request);
    }

    @PostMapping("/appointments/{id}/complete")
    public Appointment complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return operationalService.complete(jwt, id);
    }

    @PatchMapping("/appointments/{id}/cancel")
    public Appointment cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return operationalService.cancelPatient(jwt, id);
    }

    @GetMapping("/appointments/{id}/queue-position")
    public QueuePositionResponse queuePosition(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return operationalService.queuePosition(jwt, id);
    }

    @PostMapping("/queues/{id}/prioritise")
    public Queue prioritise(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                            @Valid @RequestBody PriorityRequest request) {
        return operationalService.prioritise(jwt, id, request);
    }

    @PostMapping("/queues/call-next")
    public Appointment callNext(@AuthenticationPrincipal Jwt jwt) {
        return operationalService.callNext(jwt);
    }
}
