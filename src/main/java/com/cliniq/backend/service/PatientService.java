package com.cliniq.backend.service;

import com.cliniq.backend.model.Patient;
import com.cliniq.backend.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PatientService {
    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    public Patient getPatientById(UUID id) {
        return patientRepository.findById(id).orElseThrow(() -> new RuntimeException("Patient not found with id: " + id));
    }

    public Patient getOwnProfile(UUID authUserId) {
        return patientRepository.findByAuthUserId(authUserId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient profile not found"));
    }

    @Transactional
    public Patient createPatient(Patient patient, UUID authUserId) {
        if (patient.getPatientID() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient ID must not be supplied");
        }
        if (patientRepository.findByAuthUserId(authUserId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A patient profile already exists for this account");
        }
        patient.setAuthUserId(authUserId);
        return patientRepository.save(patient);
    }

    public Patient updatePatient(UUID id, Patient patientDetails) {
        Patient patient = getPatientById(id);
        patient.setPatientName(patientDetails.getPatientName());
        patient.setPatientSurname(patientDetails.getPatientSurname());
        patient.setPhone(patientDetails.getPhone());
        patient.setPatientEmail(patientDetails.getPatientEmail());
        patient.setNationalID(patientDetails.getNationalID());
        return patientRepository.save(patient);
    }

    public void deletePatient(UUID id) {
        if (!patientRepository.existsById(id)) {
            throw new RuntimeException("Patient not found with id: " + id);
        }
        patientRepository.deleteById(id);
    }
}
