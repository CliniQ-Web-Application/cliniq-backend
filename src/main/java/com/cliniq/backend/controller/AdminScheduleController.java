package com.cliniq.backend.controller;

import com.cliniq.backend.dto.AdminScheduleRequest;
import com.cliniq.backend.dto.AdminScheduleResponse;
import com.cliniq.backend.service.ScheduleService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/schedules")
public class AdminScheduleController {
    private final ScheduleService schedules;

    public AdminScheduleController(ScheduleService schedules) { this.schedules = schedules; }

    @GetMapping
    public List<AdminScheduleResponse> list(@AuthenticationPrincipal Jwt jwt) { return schedules.listAdmin(jwt); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminScheduleResponse create(@AuthenticationPrincipal Jwt jwt, @RequestBody AdminScheduleRequest request) {
        return schedules.createAdmin(jwt, request);
    }

    @PutMapping("/{id}")
    public AdminScheduleResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @RequestBody AdminScheduleRequest request) {
        return schedules.updateAdmin(jwt, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { schedules.deleteAdmin(jwt, id); }
}
