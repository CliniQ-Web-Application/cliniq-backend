package com.cliniq.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name ="patient")
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID patientID;

    @JsonIgnore
    @Column (name = "auth_user_id", unique = true, nullable = false)
    private UUID authUserId;

    @Column(nullable = false)
    private String patientName;

    @Column(nullable = false)
    private String patientSurname;

    @Column(nullable = false)
    private String nationalID;

    @Column (nullable = false, name = "email")
    private String patientEmail;

    @Column
    private String phone;

    public Patient() {
    }

    public Patient(UUID patientID, UUID authUserId, String patientName, String patientSurname, String nationalID, String patientEmail, String phone) {
        this.patientID = patientID;
        this.authUserId = authUserId;
        this.patientName = patientName;
        this.patientSurname = patientSurname;
        this.nationalID = nationalID;
        this.patientEmail = patientEmail;
        this.phone = phone;
    }

    public UUID getPatientID() {
        return patientID;
    }

    public void setPatientID(UUID patientID) {
        this.patientID = patientID;
    }

    public UUID getAuthUserId() {
        return authUserId;
    }

    public void setAuthUserId(UUID authUserId) {
        this.authUserId = authUserId;
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

    public String getNationalID() {
        return nationalID;
    }

    public void setNationalID(String nationalID) {
        this.nationalID = nationalID;
    }

    public String getPatientEmail() {
        return patientEmail;
    }

    public void setPatientEmail(String patientEmail) {
        this.patientEmail = patientEmail;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
