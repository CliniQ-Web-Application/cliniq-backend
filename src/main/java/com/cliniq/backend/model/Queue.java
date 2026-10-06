package com.cliniq.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.LocalTime;

@Entity
public class Queue {
    @Id
    private int queueID;
    private int position;
    private String status;
    private LocalTime TimeStamp;
    private int appointmentID;
    private int departmentID;

}
