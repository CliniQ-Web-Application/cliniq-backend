package com.cliniq.backend.dto;

import com.cliniq.backend.model.Patient;

import java.util.UUID;

/** Patient projection that omits the internal Supabase auth user id. */
public record PatientResponse(
        UUID patientID,
        String patientName,
        String patientSurname,
        String nationalID,
        String patientEmail,
        String phone
) {
    public static PatientResponse from(Patient patient) {
        if (patient == null) return null;
        return new PatientResponse(
                patient.getPatientID(),
                patient.getPatientName(),
                patient.getPatientSurname(),
                patient.getNationalID(),
                patient.getPatientEmail(),
                patient.getPhone()
        );
    }
}
