package com.cliniq.backend.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name ="patient")
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID patientID;

    @Column(nullable = false)
    private String PatientName;

    @Column(nullable = false)
    private String PatientSurname;

    @Column(nullable = false)
    private int nationalID;

    @Column
    private String phone;

    public Patient() {
    }

    public Patient(UUID patientID, String patientName, String patientSurname, int nationalID, String phone) {
        this.patientID = patientID;
        PatientName = patientName;
        PatientSurname = patientSurname;
        this.nationalID = nationalID;
        this.phone = phone;
    }

    public UUID getPatientID() {
        return patientID;
    }

    public void setPatientID(UUID patientID) {
        this.patientID = patientID;
    }

    public String getPatientName() {
        return PatientName;
    }

    public void setPatientName(String patientName) {
        PatientName = patientName;
    }

    public String getPatientSurname() {
        return PatientSurname;
    }

    public void setPatientSurname(String patientSurname) {
        PatientSurname = patientSurname;
    }

    public int getNationalID() {
        return nationalID;
    }

    public void setNationalID(int nationalID) {
        this.nationalID = nationalID;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
