package com.cliniq.backend.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name ="patient")
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID patientID;

    @Column(nullable = false)
    private String PatientName;

    @Column(nullable = false)
    private String PatientSurname;

    @Column(nullable = false)
    private int nationalID;

    @Column
    private String phone; // the phone number can have +

}
