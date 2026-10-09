package com.cliniq.backend.model;

import com.cliniq.backend.enums.AvailabilityStatus;
import jakarta.persistence.*;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "queue")
public class Queue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID queueId;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false)
    private AvailabilityStatus  availabilityStatus;

    @Column(name = "time_stamp", nullable = false)
    private LocalTime timeStamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

}
