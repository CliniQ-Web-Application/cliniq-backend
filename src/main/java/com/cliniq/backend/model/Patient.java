package com.cliniq.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Patient {
    @Id
    private int patientID;
    private String PatientName;
    private String PatientSurname;
    private int nationalID;
    private String phone; // the phone number can have +

}
