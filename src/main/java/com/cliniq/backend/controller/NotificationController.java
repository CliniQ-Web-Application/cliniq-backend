package com.cliniq.backend.controller;

import com.cliniq.backend.dto.NotificationResponse;
import com.cliniq.backend.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/me")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications(@AuthenticationPrincipal Jwt jwt) {
        UUID authUserId = UUID.fromString(jwt.getSubject());

        List<NotificationResponse> response = notificationService.getMyNotifications(authUserId)
                .stream()
                .map(NotificationResponse::from)
                .toList();

        return ResponseEntity.ok(response);
    }
}