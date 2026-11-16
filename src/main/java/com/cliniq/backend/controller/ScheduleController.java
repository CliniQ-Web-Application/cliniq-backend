package com.cliniq.backend.controller;

import com.cliniq.backend.dto.PageResponse;
import com.cliniq.backend.dto.ScheduleResponse;
import com.cliniq.backend.model.Schedule;
import com.cliniq.backend.service.ScheduleService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {
    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ScheduleResponse>> getAll(
            @PageableDefault(size = 200, sort = "slotDate") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(scheduleService.getSchedules(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScheduleResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(scheduleService.getScheduleDto(id));
    }

    @PostMapping
    public ResponseEntity<ScheduleResponse> create(@RequestBody Schedule schedule,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.createScheduleDto(jwt, schedule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ScheduleResponse> update(@PathVariable UUID id, @RequestBody Schedule schedule,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(scheduleService.updateScheduleDto(id, schedule, jwt));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        scheduleService.deleteSchedule(id, jwt);
        return ResponseEntity.noContent().build();
    }
}
