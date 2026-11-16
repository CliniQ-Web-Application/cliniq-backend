package com.cliniq.backend.operational;

import com.cliniq.backend.enums.*;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.service.BookingService;
import com.cliniq.backend.service.OperationalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OperationalCancellationTest extends OperationalFixture {
    @Test
    void adminCancellationReusesSlotFirstCoreAndAuditsTheAdminActor() {
        Appointment appointment = entry(1, AppointmentStatus.BOOKED).getAppointment();
        var slot = slot(appointment);
        var adminJwt = jwt(UUID.randomUUID());
        assertSame(appointment, service.cancelAdmin(adminJwt, appointment.getAppointmentId()));
        var order = inOrder(policy, appointments, schedules, audit);
        order.verify(policy).requireAdmin(adminJwt);
        order.verify(appointments).findSlotIdByAppointmentId(appointment.getAppointmentId());
        order.verify(schedules).findByIdForUpdate(slot.getSlotId());
        order.verify(appointments).findByIdForUpdate(appointment.getAppointmentId());
        order.verify(appointments).saveAndFlush(appointment);
        order.verify(appointments).countOccupyingBySlotId(slot.getSlotId());
        order.verify(schedules).saveAndFlush(slot);
        order.verify(audit).record("APPOINTMENT_CANCELLED", "Administrator cancelled appointment", "Appointment", appointment.getAppointmentId(), adminJwt);
        assertEquals(AppointmentStatus.CANCELLED, appointment.getAppointmentStatus());
        assertEquals(SlotStatus.OPEN, slot.getSlotStatus());
        assertThrows(ResponseStatusException.class, () -> service.cancelAdmin(adminJwt, appointment.getAppointmentId()));
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = "BOOKED", mode = EnumSource.Mode.EXCLUDE)
    void adminAlsoCannotCancelOtherStates(AppointmentStatus status) {
        Appointment appointment = entry(1, status).getAppointment();
        slot(appointment);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.cancelAdmin(jwt, appointment.getAppointmentId())).getStatusCode());
        verify(appointments, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void adminAuthorizationFailsBeforeAnyLockOrRead() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(policy).requireAdmin(jwt);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.cancelAdmin(jwt, UUID.randomUUID())).getStatusCode());
        verifyNoInteractions(appointments, schedules, audit);
    }

    @Test
    void locksSlotThenReloadsAppointmentBeforeMutationFlushCountAndAudit() {
        Appointment appointment = entry(1, AppointmentStatus.BOOKED).getAppointment();
        var slot = slot(appointment);
        when(schedules.findByIdForUpdate(slot.getSlotId())).thenAnswer(call -> {
            assertEquals(AppointmentStatus.BOOKED, appointment.getAppointmentStatus());
            return Optional.of(slot);
        });
        assertSame(appointment, service.cancelPatient(jwt, appointment.getAppointmentId()));
        var order = inOrder(appointments, schedules, audit);
        order.verify(appointments).findSlotIdByAppointmentId(appointment.getAppointmentId());
        order.verify(schedules).findByIdForUpdate(slot.getSlotId());
        order.verify(appointments).findByIdForUpdate(appointment.getAppointmentId());
        order.verify(appointments).saveAndFlush(appointment);
        order.verify(appointments).countOccupyingBySlotId(slot.getSlotId());
        order.verify(schedules).saveAndFlush(slot);
        order.verify(audit).record(eq("APPOINTMENT_CANCELLED"), anyString(), eq("Appointment"), eq(appointment.getAppointmentId()), eq(jwt));
        assertEquals(AppointmentStatus.CANCELLED, appointment.getAppointmentStatus());
        assertEquals(SlotStatus.OPEN, slot.getSlotStatus());
        assertSame(slot, appointment.getSlot());
        verify(appointments, never()).findById(any());
        verify(appointments, never()).delete(any());
    }

    @Test
    void observesCancellationCommittedWhileWaitingForSlotAndNeverReleasesTwice() {
        Appointment appointment = entry(1, AppointmentStatus.BOOKED).getAppointment();
        var slot = slot(appointment);
        when(schedules.findByIdForUpdate(slot.getSlotId())).thenAnswer(call -> {
            appointment.setAppointmentStatus(AppointmentStatus.CANCELLED);
            return Optional.of(slot);
        });
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.cancelPatient(jwt, appointment.getAppointmentId())).getStatusCode());
        verify(appointments, never()).saveAndFlush(any());
        verify(appointments, never()).countOccupyingBySlotId(any());
        verify(schedules, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void sequentialDoubleCancellationAlsoWritesOnlyOnce() {
        Appointment appointment = entry(1, AppointmentStatus.BOOKED).getAppointment();
        slot(appointment);
        service.cancelPatient(jwt, appointment.getAppointmentId());
        assertThrows(ResponseStatusException.class, () -> service.cancelPatient(jwt, appointment.getAppointmentId()));
        verify(appointments, times(1)).saveAndFlush(appointment);
        verify(schedules, times(1)).saveAndFlush(any());
        verify(audit, times(1)).record(anyString(), anyString(), anyString(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = SlotStatus.class, names = {"BLOCKED", "OPEN"})
    void neverReopensBlockedOrAlreadyOpenSlots(SlotStatus status) {
        Appointment appointment = entry(1, AppointmentStatus.BOOKED).getAppointment();
        var slot = slot(appointment);
        slot.setSlotStatus(status);
        service.cancelPatient(jwt, appointment.getAppointmentId());
        assertEquals(status, slot.getSlotStatus());
        verify(schedules, never()).saveAndFlush(any());
        verify(appointments, never()).countOccupyingBySlotId(any());
    }

    @Test
    void doesNotReopenWhenRemainingOccupancyStillMeetsEffectiveCapacity() {
        Appointment appointment = entry(1, AppointmentStatus.BOOKED).getAppointment();
        var slot = slot(appointment);
        slot.setCapacity(2);
        when(appointments.countOccupyingBySlotId(slot.getSlotId())).thenReturn(2L);
        service.cancelPatient(jwt, appointment.getAppointmentId());
        assertEquals(SlotStatus.BOOKED, slot.getSlotStatus());
        verify(schedules, never()).saveAndFlush(any());
    }

    @Test
    void cancellationPermitsRebookingWithoutDeletingHistory() {
        Appointment old = entry(1, AppointmentStatus.BOOKED).getAppointment();
        var slot = slot(old);
        slot.setCapacity(2);
        slot.setSlotDate(LocalDate.of(2030, 1, 2));
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(11, 0));
        Appointment other = new Appointment();
        other.setAppointmentStatus(AppointmentStatus.BOOKED);
        List<Appointment> history = new ArrayList<>(List.of(old, other));
        when(appointments.countOccupyingBySlotId(slot.getSlotId())).thenAnswer(call -> history.stream()
                .filter(a -> a.getAppointmentStatus() != AppointmentStatus.CANCELLED).count());
        when(appointments.saveAndFlush(any())).thenAnswer(call -> {
            Appointment a = call.getArgument(0);
            if (!history.contains(a)) history.add(a);
            return a;
        });
        when(patients.findByAuthUserId(userId)).thenReturn(Optional.of(patient));
        service.cancelPatient(jwt, old.getAppointmentId());
        assertEquals(SlotStatus.OPEN, slot.getSlotStatus());
        var bookings = new BookingService(schedules, appointments, patients, policy,
                Clock.fixed(Instant.parse("2030-01-01T09:00:00Z"), ZoneOffset.UTC));
        bookings.book(userId, slot.getSlotId());
        assertEquals(3, history.size());
        assertEquals(AppointmentStatus.CANCELLED, old.getAppointmentStatus());
        assertEquals(SlotStatus.BOOKED, slot.getSlotStatus());
        assertThrows(ResponseStatusException.class, () -> bookings.book(userId, slot.getSlotId()));
    }

    @Test
    void rejectsOtherPatientsWithoutMutation() {
        Appointment appointment = entry(1, AppointmentStatus.BOOKED).getAppointment();
        var slot = slot(appointment);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.cancelPatient(jwt(UUID.randomUUID()), appointment.getAppointmentId())).getStatusCode());
        assertEquals(AppointmentStatus.BOOKED, appointment.getAppointmentStatus());
        assertEquals(SlotStatus.BOOKED, slot.getSlotStatus());
        verify(appointments, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = "BOOKED", mode = EnumSource.Mode.EXCLUDE)
    void rejectsNonBookedStatuses(AppointmentStatus status) {
        Appointment appointment = entry(1, status).getAppointment();
        slot(appointment);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.cancelPatient(jwt, appointment.getAppointmentId())).getStatusCode());
        verify(appointments, never()).saveAndFlush(any());
        verify(schedules, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void missingAppointmentReturns404AndLegacySlotlessBookingCanCancel() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.cancelPatient(jwt, UUID.randomUUID())).getStatusCode());
        Appointment legacy = entry(1, AppointmentStatus.BOOKED).getAppointment();
        service.cancelPatient(jwt, legacy.getAppointmentId());
        assertEquals(AppointmentStatus.CANCELLED, legacy.getAppointmentStatus());
        verifyNoInteractions(schedules);
    }

    /** Exercises Spring transaction boundaries; no claim of real database rollback. */
    @ParameterizedTest
    @ValueSource(strings = {"none", "appointment", "slot", "audit", "admin-none", "admin-appointment", "admin-slot", "admin-audit"})
    void cancellationSlotAndAuditShareCommitOrRollback(String failure) {
        Appointment appointment = entry(1, AppointmentStatus.BOOKED).getAppointment();
        var slot = slot(appointment);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(call -> {
            assertFalse(call.<TransactionDefinition>getArgument(0).isReadOnly());
            return new SimpleTransactionStatus();
        });
        if (failure.endsWith("appointment")) when(appointments.saveAndFlush(any())).thenThrow(new IllegalStateException());
        if (failure.endsWith("slot")) when(schedules.saveAndFlush(any())).thenThrow(new IllegalStateException());
        if (failure.endsWith("audit")) doThrow(new IllegalStateException()).when(audit)
                .record(anyString(), anyString(), anyString(), any(), any());
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        OperationalService proxy = (OperationalService) factory.getProxy();
        Runnable cancel = () -> {
            if (failure.startsWith("admin-")) proxy.cancelAdmin(jwt, appointment.getAppointmentId());
            else proxy.cancelPatient(jwt, appointment.getAppointmentId());
        };
        if (failure.endsWith("none")) {
            cancel.run();
            var order = inOrder(appointments, schedules, audit, transactions);
            order.verify(appointments).saveAndFlush(appointment);
            order.verify(schedules).saveAndFlush(slot);
            order.verify(audit).record(anyString(), anyString(), anyString(), any(), any());
            order.verify(transactions).commit(any(TransactionStatus.class));
            verify(transactions, never()).rollback(any());
        } else {
            assertThrows(IllegalStateException.class, cancel::run);
            verify(transactions).rollback(any());
            verify(transactions, never()).commit(any());
        }
    }
}
