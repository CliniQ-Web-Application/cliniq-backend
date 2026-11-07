package com.cliniq.backend.booking;

import com.cliniq.backend.controller.PatientController;
import com.cliniq.backend.dto.PatientProfileRequest;
import com.cliniq.backend.model.Patient;
import com.cliniq.backend.repository.PatientRepository;
import com.cliniq.backend.service.AccessPolicy;
import com.cliniq.backend.service.PatientService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingPatientProfileTest {
    private final PatientRepository patients = mock(PatientRepository.class);
    private final PatientService service = new PatientService(patients);
    private final AccessPolicy access = mock(AccessPolicy.class);
    private final PatientController controller = new PatientController(service, access);
    private final UUID userId = UUID.randomUUID();
    private final PatientProfileRequest request = new PatientProfileRequest(" Ada ", " Patient ", " 12345 ", " 123456 ");

    private Jwt jwt(String email) {
        var builder = Jwt.withTokenValue("test").header("alg", "test").subject(userId.toString());
        if (email != null) builder.claim("email", email);
        return builder.build();
    }

    @Test
    void createsProfileForJwtSubjectAndEmailOnly() {
        when(patients.findByAuthUserId(userId)).thenReturn(Optional.empty());
        when(patients.save(any())).thenAnswer(call -> call.getArgument(0));
        Patient result = controller.completeOwnProfile(jwt("verified@example.com"), request);
        verify(access).requirePatient(userId);
        assertEquals(userId, result.getAuthUserId());
        assertEquals("verified@example.com", result.getPatientEmail());
        assertEquals("Ada", result.getPatientName());
        assertEquals("Patient", result.getPatientSurname());
        assertEquals("12345", result.getNationalID());
        assertEquals("123456", result.getPhone());
        assertNull(result.getPatientID());
    }

    @Test
    void repeatedProfileCompletionReturnsExistingWithoutOverwritingIt() {
        Patient existing = new Patient();
        existing.setPatientID(UUID.randomUUID());
        existing.setAuthUserId(userId);
        existing.setPatientName("Original");
        when(patients.findByAuthUserId(userId)).thenReturn(Optional.of(existing));
        assertSame(existing, controller.completeOwnProfile(jwt("verified@example.com"), request));
        verify(patients, never()).save(any());
        assertEquals("Original", existing.getPatientName());
    }

    @Test
    void getsOnlyOwnProfileAndExposesAgreedIdentifierName() {
        Patient own = new Patient();
        own.setPatientID(UUID.randomUUID());
        own.setAuthUserId(userId);
        when(patients.findByAuthUserId(userId)).thenReturn(Optional.of(own));
        assertSame(own, controller.getOwnProfile(jwt("verified@example.com")));
        verify(access).requirePatient(userId);
        var json = JsonMapper.builder().build().valueToTree(own);
        assertEquals(own.getPatientID().toString(), json.get("patientID").asText());
    }

    @Test
    void missingProfileReturns404WithoutCreation() {
        when(patients.findByAuthUserId(userId)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> controller.getOwnProfile(jwt("verified@example.com"))).getStatusCode());
        verify(patients, never()).save(any());
    }

    @Test
    void rejectsMissingOrInvalidEmailAndWhitespaceProfileValues() {
        when(patients.findByAuthUserId(userId)).thenReturn(Optional.empty());
        for (String email : new String[]{null, "invalid", "some one@example.com", "some\tone@example.com"}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> controller.completeOwnProfile(jwt(email), request)).getStatusCode());
        }
        for (PatientProfileRequest invalid : new PatientProfileRequest[]{
                new PatientProfileRequest(" ", "Patient", "12345", "123456"),
                new PatientProfileRequest("Ada", null, "12345", "123456"),
                new PatientProfileRequest("Ada", "Patient", " ", "123456"),
                new PatientProfileRequest("Ada", "Patient", "12345", " "),
                new PatientProfileRequest("x".repeat(256), "Patient", "12345", "123456")}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> controller.completeOwnProfile(jwt("verified@example.com"), invalid)).getStatusCode());
        }
        verify(patients, never()).save(any());
    }

    @Test
    void legacyCreateCannotOverwriteSomeoneElsesRecordBySupplyingId() {
        Patient spoofed = new Patient();
        spoofed.setPatientID(UUID.randomUUID());
        spoofed.setAuthUserId(UUID.randomUUID());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.createPatient(spoofed, jwt("verified@example.com"))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.createPatient(spoofed, userId)).getStatusCode());
        verifyNoInteractions(patients);
    }

    @Test
    void legacyCreateDiscardsSpoofedOwnershipAndEmail() {
        Patient spoofed = new Patient();
        spoofed.setAuthUserId(UUID.randomUUID());
        spoofed.setPatientEmail("someone-else@example.com");
        spoofed.setPatientName("Ada");
        spoofed.setPatientSurname("Patient");
        spoofed.setNationalID("12345");
        spoofed.setPhone("123456");
        when(patients.findByAuthUserId(userId)).thenReturn(Optional.empty());
        when(patients.save(any())).thenAnswer(call -> call.getArgument(0));
        controller.createPatient(spoofed, jwt("verified@example.com"));
        var saved = ArgumentCaptor.forClass(Patient.class);
        verify(patients).save(saved.capture());
        assertEquals(userId, saved.getValue().getAuthUserId());
        assertEquals("verified@example.com", saved.getValue().getPatientEmail());
    }

    @Test
    void staffCannotCompletePatientProfile() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient accounts only"))
                .when(access).requirePatient(userId);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.completeOwnProfile(jwt("verified@example.com"), request)).getStatusCode());
        verifyNoInteractions(patients);
    }
}
