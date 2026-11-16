package com.cliniq.backend.controller;

import com.cliniq.backend.dto.ScheduleBroadcastRequest;
import com.cliniq.backend.dto.ScheduleBroadcastResponse;
import com.cliniq.backend.service.ScheduleBroadcastService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleBroadcastController {
    private final ScheduleBroadcastService broadcasts;

    public ScheduleBroadcastController(ScheduleBroadcastService broadcasts) { this.broadcasts = broadcasts; }

    @PostMapping("/{id}/broadcast")
    public ScheduleBroadcastResponse broadcast(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                @RequestBody ScheduleBroadcastRequest request) {
        return broadcasts.broadcast(jwt, id, request);
    }
}
