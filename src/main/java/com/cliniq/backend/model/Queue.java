package com.cliniq.backend.model;

import com.cliniq.backend.enums.AvailabilityStatus;
import jakarta.persistence.*;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "queue")
public class Queue {

    public Queue() {
    }

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

    public UUID getQueueId() { return queueId; }
    public void setQueueId(UUID queueId) { this.queueId = queueId; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
    public AvailabilityStatus getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(AvailabilityStatus availabilityStatus) { this.availabilityStatus = availabilityStatus; }
    public LocalTime getTimeStamp() { return timeStamp; }
    public void setTimeStamp(LocalTime timeStamp) { this.timeStamp = timeStamp; }
    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
}