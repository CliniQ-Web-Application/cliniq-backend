package com.cliniq.backend.service;

import com.cliniq.backend.model.Notification;
import com.cliniq.backend.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final AuditLogsService auditLogsService;

    @Autowired
    public NotificationService(NotificationRepository notificationRepository, AuditLogsService auditLogsService) {
        this.notificationRepository = notificationRepository;
        this.auditLogsService = auditLogsService;
    }

    public NotificationService(NotificationRepository notificationRepository) {
        this(notificationRepository, null);
    }

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }

    public Notification getNotificationById(UUID id) {
        return notificationRepository.findById(id).orElseThrow(() -> new RuntimeException("Notification not found with id: " + id));
    }

    public Notification createNotification(Notification notification) {
        Notification saved = notificationRepository.save(notification);
        if (auditLogsService != null) auditLogsService.record("NOTIFICATION_CREATED", "Notification created", "Notification", saved.getNotificationId(), null);
        return saved;
    }

    public Notification updateNotification(UUID id, Notification details) {
        Notification notification = getNotificationById(id);
        notification.setMessage(details.getMessage());
        notification.setType(details.getType());
        notification.setIsRead(details.getIsRead());
        notification.setCreatedAt(details.getCreatedAt());
        notification.setPatient(details.getPatient());
        notification.setAppointment(details.getAppointment());
        Notification saved = notificationRepository.save(notification);
        if (auditLogsService != null) auditLogsService.record("NOTIFICATION_UPDATED", "Notification updated", "Notification", saved.getNotificationId(), null);
        return saved;
    }

    public void deleteNotification(UUID id) {
        if (!notificationRepository.existsById(id)) {
            throw new RuntimeException("Notification not found with id: " + id);
        }
        notificationRepository.deleteById(id);
        if (auditLogsService != null) auditLogsService.record("NOTIFICATION_DELETED", "Notification deleted", "Notification", id, null);
    }
}