package com.cliniq.backend.service;

import com.cliniq.backend.dto.NotificationResponse;
import com.cliniq.backend.model.Notification;
import com.cliniq.backend.repository.NotificationRepository;
import com.cliniq.backend.repository.StaffRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final StaffRepository staffRepository;

    public NotificationService(NotificationRepository notificationRepository, StaffRepository staffRepository) {
        this.notificationRepository = notificationRepository;
        this.staffRepository = staffRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(Jwt jwt) {
        UUID authUserId = AccessPolicy.subject(jwt);
        boolean isStaff = staffRepository.findByAuthUserId(authUserId).isPresent();
        List<Notification> list = isStaff
                ? notificationRepository.findByPatientIsNullAndAppointment_Staff_AuthUserIdOrderByCreatedAtDesc(authUserId)
                : notificationRepository.findByPatient_AuthUserIdOrderByCreatedAtDesc(authUserId);
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional
    public NotificationResponse markAsRead(Jwt jwt, UUID notificationId) {
        UUID authUserId = AccessPolicy.subject(jwt);
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found."));
        if (!belongsToUser(authUserId, n)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot modify this notification.");
        }
        n.setIsRead(true);
        return toResponse(notificationRepository.save(n));
    }

    @Transactional
    public void markAllAsRead(Jwt jwt) {
        UUID authUserId = AccessPolicy.subject(jwt);
        boolean isStaff = staffRepository.findByAuthUserId(authUserId).isPresent();
        List<Notification> list = isStaff
                ? notificationRepository.findByPatientIsNullAndAppointment_Staff_AuthUserIdOrderByCreatedAtDesc(authUserId)
                : notificationRepository.findByPatient_AuthUserIdOrderByCreatedAtDesc(authUserId);
        list.stream()
                .filter(n -> !Boolean.TRUE.equals(n.getIsRead()))
                .forEach(n -> {
                    n.setIsRead(true);
                    notificationRepository.save(n);
                });
    }

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }

    public Notification getNotificationById(UUID id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found with id: " + id));
    }

    public Notification createNotification(Notification notification) {
        return notificationRepository.save(notification);
    }

    public Notification updateNotification(UUID id, Notification details) {
        Notification notification = getNotificationById(id);
        notification.setMessage(details.getMessage());
        notification.setType(details.getType());
        notification.setIsRead(details.getIsRead());
        notification.setCreatedAt(details.getCreatedAt());
        notification.setPatient(details.getPatient());
        notification.setAppointment(details.getAppointment());
        return notificationRepository.save(notification);
    }

    public void deleteNotification(UUID id) {
        if (!notificationRepository.existsById(id)) {
            throw new RuntimeException("Notification not found with id: " + id);
        }
        notificationRepository.deleteById(id);
    }

    private boolean belongsToUser(UUID authUserId, Notification n) {
        if (n.getPatient() != null) {
            return authUserId.equals(n.getPatient().getAuthUserId());
        }
        return n.getAppointment() != null
                && n.getAppointment().getStaff() != null
                && authUserId.equals(n.getAppointment().getStaff().getAuthUserId());
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getNotificationId(),
                n.getMessage(),
                n.getType(),
                Boolean.TRUE.equals(n.getIsRead()),
                n.getCreatedAt(),
                n.getAppointment() != null ? n.getAppointment().getAppointmentId() : null
        );
    }
}