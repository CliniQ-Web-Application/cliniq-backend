package com.cliniq.backend.controller;

import com.cliniq.backend.model.Patient;
import com.cliniq.backend.dto.PatientProfileRequest;
import com.cliniq.backend.service.AccessPolicy;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import com.cliniq.backend.service.PatientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;
    private final AccessPolicy accessPolicy;

    public PatientController(PatientService patientService, AccessPolicy accessPolicy) {
        this.patientService = patientService;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping("/me")
    public Patient getOwnProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AccessPolicy.subject(jwt);
        accessPolicy.requirePatient(userId);
        return patientService.getOwnProfile(userId);
    }

    @PostMapping("/me")
    public Patient completeOwnProfile(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody PatientProfileRequest request) {
        UUID userId = AccessPolicy.subject(jwt);
        accessPolicy.requirePatient(userId);
        try {
            return patientService.getOwnProfile(userId);
        } catch (ResponseStatusException absent) {
            if (absent.getStatusCode() != HttpStatus.NOT_FOUND) throw absent;
        }
        return patientService.createPatient(profile(request, jwt), userId);
    }

    private Patient profile(PatientProfileRequest request, Jwt jwt) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient profile details are required");
        }
        String email = jwt.getClaimAsString("email");
        if (email == null || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email is required on your sign-in account");
        }
        Patient patient = new Patient();
        patient.setPatientName(required(request.patientName(), "First name"));
        patient.setPatientSurname(required(request.patientSurname(), "Surname"));
        patient.setNationalID(required(request.nationalID(), "National ID"));
        patient.setPhone(required(request.phone(), "Phone"));
        patient.setPatientEmail(email);
        return patient;
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " is required and must be at most 255 characters");
        }
        return value.trim();
    }

    @GetMapping
    public ResponseEntity<List<Patient>> getAllPatients() {
        return ResponseEntity.ok(patientService.getAllPatients());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Patient> getPatientById(@PathVariable UUID id) {
        return ResponseEntity.ok(patientService.getPatientById(id));
    }

    @PostMapping
    public ResponseEntity<Patient> createPatient(@RequestBody Patient patient, @AuthenticationPrincipal Jwt jwt) {
        UUID authUserId = AccessPolicy.subject(jwt);
        accessPolicy.requirePatient(authUserId);
        if (patient.getPatientID() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient ID must not be supplied");
        }
        Patient created = patientService.createPatient(profile(new PatientProfileRequest(
                patient.getPatientName(), patient.getPatientSurname(), patient.getNationalID(), patient.getPhone()), jwt), authUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Patient> updatePatient(@PathVariable UUID id, @RequestBody Patient patient) {
        return ResponseEntity.ok(patientService.updatePatient(id, patient));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePatient(@PathVariable UUID id) {
        patientService.deletePatient(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> statusError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(Map.of("message", error.getReason() == null ? "Request failed" : error.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalidProfile() {
        return ResponseEntity.badRequest().body(Map.of("message", "Provide your first name, surname, national ID, and phone (at most 255 characters each)"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> profileConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Patient profile could not be saved. Reload to check whether it already exists."));
    }
}
