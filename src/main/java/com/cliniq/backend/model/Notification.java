package com.cliniq.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
public class Notification {
    @Id
    private Integer notificationId;
    private String message;
    private String type;
    private Boolean isRead;
    private LocalDateTime createdAt;
    private Integer patientId;
    private Integer appointmentId;
}//
