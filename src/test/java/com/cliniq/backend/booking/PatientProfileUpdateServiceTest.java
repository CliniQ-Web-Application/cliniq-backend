package com.cliniq.backend.booking;

import com.cliniq.backend.model.Patient;
import com.cliniq.backend.repository.PatientRepository;
import com.cliniq.backend.service.AuditLogsService;
import com.cliniq.backend.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises repository writes and Spring transaction boundaries without a database. */
class PatientProfileUpdateServiceTest {
    private final PatientRepository patients = mock(PatientRepository.class);
    private final AuditLogsService audits = mock(AuditLogsService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final TransactionStatus transaction = new SimpleTransactionStatus();
    private final UUID userId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final Jwt jwt = Jwt.withTokenValue("test").header("alg", "test").subject(userId.toString()).build();
    private final Patient existing = new Patient(patientId, userId, "Original", "Surname", "old-id", "stored@example.com", "old-phone");
    private final Patient details = new Patient(UUID.randomUUID(), UUID.randomUUID(), "Ada", "Patient", "12345", "spoofed@example.com", "123456");
    private PatientService service;

    @BeforeEach
    void setUp() {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        ProxyFactory factory = new ProxyFactory(new PatientService(patients, audits));
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        service = (PatientService) factory.getProxy();
        when(patients.findByAuthUserId(userId)).thenReturn(Optional.of(existing));
        when(patients.save(any(Patient.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void savesOnlyAuthenticatedUsersEditableFieldsAndAuditsBeforeCommit() {
        Patient result = service.updateOwnProfile(userId, details, jwt);
        assertSame(existing, result);
        assertEquals(patientId, result.getPatientID());
        assertEquals(userId, result.getAuthUserId());
        assertEquals("stored@example.com", result.getPatientEmail());
        assertEquals("Ada", result.getPatientName());
        assertEquals("Patient", result.getPatientSurname());
        assertEquals("12345", result.getNationalID());
        assertEquals("123456", result.getPhone());
        assertSame(existing, service.getOwnProfile(userId));
        var order = inOrder(transactions, patients, audits);
        order.verify(transactions).getTransaction(any());
        order.verify(patients).findByAuthUserId(userId);
        order.verify(patients).save(existing);
        order.verify(audits).record("PATIENT_PROFILE_UPDATED", "Patient profile updated", "Patient", patientId, jwt);
        order.verify(transactions).commit(transaction);
        verify(patients, never()).findById(any());
        verify(transactions, never()).rollback(any());
    }

    @Test
    void missingOwnProfileNeverLooksUpSuppliedPatientIdOrCreatesProfile() {
        when(patients.findByAuthUserId(userId)).thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateOwnProfile(userId, details, jwt));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(patients).findByAuthUserId(userId);
        verifyNoMoreInteractions(patients);
        verifyNoInteractions(audits);
        verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
    }

    @Test
    void saveFailureRollsBackWithoutRecordingAudit() {
        when(patients.save(existing)).thenThrow(new IllegalStateException("save failed"));
        assertThrows(IllegalStateException.class, () -> service.updateOwnProfile(userId, details, jwt));
        verifyNoInteractions(audits);
        verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
    }

    @Test
    void auditFailureRollsBackTheProfileUpdateTransaction() {
        doThrow(new IllegalStateException("audit failed")).when(audits)
                .record("PATIENT_PROFILE_UPDATED", "Patient profile updated", "Patient", patientId, jwt);
        assertThrows(IllegalStateException.class, () -> service.updateOwnProfile(userId, details, jwt));
        var order = inOrder(patients, audits, transactions);
        order.verify(patients).save(existing);
        order.verify(audits).record("PATIENT_PROFILE_UPDATED", "Patient profile updated", "Patient", patientId, jwt);
        order.verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
    }
}
