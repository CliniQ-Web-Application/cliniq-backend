package com.cliniq.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
public class Appointment {
    @Id
    private Integer appointmentId;
    private String appointmentStatus;
    private String qrCode;
    private LocalDateTime checkInTime;
    private String priority;
    private LocalDateTime createdAt;
    private LocalDateTime bookedAt;
    private LocalDateTime calledTime;
    private LocalDateTime completedTime;
    private Integer calledBy;
    private Integer patientId;
    private Integer staffId;
}
