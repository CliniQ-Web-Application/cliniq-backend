package com.cliniq.backend.service;

import com.cliniq.backend.model.Notification;
import com.cliniq.backend.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }

    public Notification getNotificationById(UUID id) {
        return notificationRepository.findById(id).orElseThrow(() -> new RuntimeException("Notification not found with id: " + id));
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
}