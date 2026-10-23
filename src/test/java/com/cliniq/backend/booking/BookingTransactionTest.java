package com.cliniq.backend.booking;

import com.cliniq.backend.enums.SlotStatus;
import com.cliniq.backend.enums.StaffRole;
import com.cliniq.backend.model.Department;
import com.cliniq.backend.model.Patient;
import com.cliniq.backend.model.Schedule;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.AppointmentRepository;
import com.cliniq.backend.repository.PatientRepository;
import com.cliniq.backend.repository.ScheduleRepository;
import com.cliniq.backend.service.AccessPolicy;
import com.cliniq.backend.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Verifies the real Spring transaction interceptor, not database persistence/rollback. */
class BookingTransactionTest {
    @Test
    void commitsOnlyAfterBothWritesComplete() {
        verifyTransaction(null);
    }

    @Test
    void insertFailureRollsBackAndNeverAttemptsSlotWrite() {
        verifyTransaction("appointment");
    }

    @Test
    void slotWriteFailureRollsBackTheSharedTransaction() {
        verifyTransaction("slot");
    }

    private void verifyTransaction(String failingWrite) {
        var schedules = mock(ScheduleRepository.class);
        var appointments = mock(AppointmentRepository.class);
        var patients = mock(PatientRepository.class);
        var policy = mock(AccessPolicy.class);
        var transactions = new RecordingTransactions();
        UUID userId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        Patient patient = new Patient(); patient.setPatientID(UUID.randomUUID());
        when(patients.findByAuthUserId(userId)).thenReturn(Optional.of(patient));
        Staff doctor = new Staff(); doctor.setStaffRole(StaffRole.DOCTOR); doctor.setActive(true);
        doctor.setDepartment(new Department());
        Schedule slot = new Schedule(); slot.setSlotId(slotId); slot.setStaff(doctor);
        slot.setSlotStatus(SlotStatus.OPEN); slot.setSlotDate(LocalDate.of(2030, 1, 2));
        slot.setStartTime(LocalTime.of(10, 0)); slot.setEndTime(LocalTime.of(10, 30));
        when(schedules.findByIdForUpdate(slotId)).thenAnswer(call -> {
            assertTrue(transactions.active); return Optional.of(slot);
        });
        when(appointments.saveAndFlush(any())).thenAnswer(call -> {
            assertTrue(transactions.active);
            if ("appointment".equals(failingWrite)) throw new IllegalStateException("insert failed");
            return call.getArgument(0);
        });
        when(schedules.saveAndFlush(any())).thenAnswer(call -> {
            assertTrue(transactions.active);
            if ("slot".equals(failingWrite)) throw new IllegalStateException("slot write failed");
            return call.getArgument(0);
        });
        BookingService target = new BookingService(schedules, appointments, patients, policy,
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC));
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        BookingService proxy = (BookingService) factory.getProxy();
        if (failingWrite == null) {
            proxy.book(userId, slotId);
            assertEquals(1, transactions.commits);
            assertEquals(0, transactions.rollbacks);
            verify(schedules).saveAndFlush(slot);
        } else {
            assertThrows(IllegalStateException.class, () -> proxy.book(userId, slotId));
            assertEquals(0, transactions.commits);
            assertEquals(1, transactions.rollbacks);
            if ("appointment".equals(failingWrite)) verify(schedules, never()).saveAndFlush(any());
        }
        assertFalse(transactions.active);
    }

    private static class RecordingTransactions implements PlatformTransactionManager {
        private boolean active;
        private int commits;
        private int rollbacks;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            assertNotNull(definition);
            assertFalse(definition.isReadOnly());
            assertFalse(active);
            active = true;
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            assertTrue(active); commits++; active = false;
        }

        @Override
        public void rollback(TransactionStatus status) {
            assertTrue(active); rollbacks++; active = false;
        }
    }
}
