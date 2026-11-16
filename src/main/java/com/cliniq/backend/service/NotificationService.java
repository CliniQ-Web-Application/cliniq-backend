package com.cliniq.backend.service;

import com.cliniq.backend.dto.NotificationResponse;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Notification;
import com.cliniq.backend.model.Patient;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.NotificationRepository;
import com.cliniq.backend.repository.PatientRepository;
import com.cliniq.backend.repository.StaffRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final StaffRepository staffRepository;
    private final PatientRepository patientRepository;
    private final AuditLogsService auditLogsService;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(UUID authUserId) {
        List<Notification> notifications = staffRepository.findByAuthUserId(authUserId).isPresent()
                ? notificationRepository.findByStaff_AuthUserIdOrderByCreatedAtDesc(authUserId)
                : notificationRepository.findByPatient_AuthUserIdOrderByCreatedAtDesc(authUserId);
        return notifications.stream().map(NotificationResponse::from).toList();
    }

    @Transactional
    public NotificationResponse markAsRead(UUID authUserId, UUID id) {
        if (authUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid authenticated user is required");
        }
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        boolean owned = staffRepository.findByAuthUserId(authUserId).isPresent()
                ? notification.getStaff() != null && authUserId.equals(notification.getStaff().getAuthUserId())
                : notification.getPatient() != null && authUserId.equals(notification.getPatient().getAuthUserId());
        if (!owned) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Notification belongs to another user");
        }
        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    @Autowired
    public NotificationService(PatientRepository patientRepository, StaffRepository staffRepository,
                               NotificationRepository notificationRepository, AuditLogsService auditLogsService) {
        this.patientRepository = patientRepository;
        this.staffRepository = staffRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogsService = auditLogsService;
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

    public void notifyStaff(Staff recipient, Appointment appointment, String type, String message) {
        if (recipient == null) return;
        Notification n = new Notification();
        n.setStaff(recipient);
        n.setAppointment(appointment);
        n.setType(type);
        n.setMessage(message);
        n.setIsRead(false);
        notificationRepository.save(n);
    }

    public void notifyPatient(Patient recipient, Appointment appointment, String type, String message) {
        if (recipient == null) return;
        Notification n = new Notification();
        n.setPatient(recipient);
        n.setAppointment(appointment);
        n.setType(type);
        n.setMessage(message);
        n.setIsRead(false);
        notificationRepository.save(n);
    }

    public void deleteNotification(UUID id) {
        if (!notificationRepository.existsById(id)) {
            throw new RuntimeException("Notification not found with id: " + id);
        }
        notificationRepository.deleteById(id);
        if (auditLogsService != null) auditLogsService.record("NOTIFICATION_DELETED", "Notification deleted", "Notification", id, null);
    }
}