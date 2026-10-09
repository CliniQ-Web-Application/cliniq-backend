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
    private String patientName;

    @Column(nullable = false)
    private String patientSurname;

    @Column(nullable = false)
    private int nationalID;

    @Column
    private String phone;

    public Patient() {
    }

    public Patient(UUID patientID, String patientName, String patientSurname, int nationalID, String phone) {
        this.patientID = patientID;
        this.patientName = patientName;
        this.patientSurname = patientSurname;
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
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getPatientSurname() {
        return patientSurname;
    }

    public void setPatientSurname(String patientSurname) {
        this.patientSurname = patientSurname;
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
