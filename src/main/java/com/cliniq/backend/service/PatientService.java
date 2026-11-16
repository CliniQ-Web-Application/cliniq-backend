package com.cliniq.backend.service;

import com.cliniq.backend.model.Patient;
import com.cliniq.backend.repository.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

@Service
public class PatientService {
    private final PatientRepository patientRepository;
    private final AuditLogsService auditLogsService;

    @Autowired
    public PatientService(PatientRepository patientRepository, AuditLogsService auditLogsService) {
        this.patientRepository = patientRepository;
        this.auditLogsService = auditLogsService;
    }

    public PatientService(PatientRepository patientRepository) {
        this(patientRepository, null);
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
    public Patient createPatient(Patient patient, UUID authUserId, Jwt jwt) {
        if (patient.getPatientID() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient ID must not be supplied");
        }
        if (patientRepository.findByAuthUserId(authUserId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A patient profile already exists for this account");
        }
        patient.setAuthUserId(authUserId);
        Patient saved = patientRepository.save(patient);
        if (auditLogsService != null) auditLogsService.record("PATIENT_PROFILE_CREATED", "Patient profile created", "Patient", saved.getPatientID(), jwt);
        return saved;
    }

    @Transactional
    public Patient savePatient(Patient patient) {
        return patientRepository.save(patient);
    }

    public Patient createPatient(Patient patient, UUID authUserId) {
        return createPatient(patient, authUserId, null);
    }

    public Patient updatePatient(UUID id, Patient patientDetails, Jwt jwt) {
        Patient patient = getPatientById(id);
        patient.setPatientName(patientDetails.getPatientName());
        patient.setPatientSurname(patientDetails.getPatientSurname());
        patient.setPhone(patientDetails.getPhone());
        patient.setPatientEmail(patientDetails.getPatientEmail());
        patient.setNationalID(patientDetails.getNationalID());
        Patient saved = patientRepository.save(patient);
        if (auditLogsService != null) auditLogsService.record("PATIENT_PROFILE_UPDATED", "Patient profile updated", "Patient", saved.getPatientID(), jwt);
        return saved;
    }

    public void deletePatient(UUID id, Jwt jwt) {
        if (!patientRepository.existsById(id)) {
            throw new RuntimeException("Patient not found with id: " + id);
        }
        patientRepository.deleteById(id);
        if (auditLogsService != null) auditLogsService.record("PATIENT_PROFILE_DELETED", "Patient profile deleted", "Patient", id, jwt);
    }
}
