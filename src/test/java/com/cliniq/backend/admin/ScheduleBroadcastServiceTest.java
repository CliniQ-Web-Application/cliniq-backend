package com.cliniq.backend.admin;

import com.cliniq.backend.dto.*;
import com.cliniq.backend.enums.*;
import com.cliniq.backend.model.*;
import com.cliniq.backend.service.ScheduleBroadcastService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduleBroadcastServiceTest extends AdminFixture {
    final ScheduleBroadcastService service = new ScheduleBroadcastService(schedules, appointments, staff, notifications, audit, clock);
    final ScheduleBroadcastRequest delay = new ScheduleBroadcastRequest(ScheduleBroadcastRequest.Type.DELAY, "Please arrive later", 30);

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void locksThenNotifiesDistinctPatientsWithRepresentativeAppointmentAndNoPatientListing() {
        actor(doctor);
        Patient first = patient(); Patient second = patient();
        List<Appointment> history = new ArrayList<>();
        for (AppointmentStatus status : AppointmentStatus.values()) history.add(appointment(first, status));
        history.add(appointment(second, AppointmentStatus.BOOKED));
        when(appointments.findBroadcastRecipients(eq(slot.getSlotId()), anyCollection())).thenAnswer(call -> {
            Collection<AppointmentStatus> allowed = call.getArgument(1);
            assertEquals(Set.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKEDIN, AppointmentStatus.WAITING), new HashSet<>(allowed));
            return history.stream().filter(a -> allowed.contains(a.getAppointmentStatus())).toList();
        });
        var response = service.broadcast(jwt, slot.getSlotId(), delay);
        assertEquals(2, response.recipientCount());
        ArgumentCaptor<Iterable<Notification>> saved = ArgumentCaptor.forClass((Class) Iterable.class);
        var order = inOrder(schedules, appointments, notifications, audit);
        order.verify(schedules).findByIdForUpdate(slot.getSlotId());
        order.verify(appointments).findBroadcastRecipients(eq(slot.getSlotId()), anyCollection());
        order.verify(notifications).saveAll(saved.capture());
        order.verify(audit).record("SCHEDULE_BROADCAST", "DELAY: 2 recipients", "Schedule", slot.getSlotId(), jwt);
        List<Notification> messages = new ArrayList<>(); saved.getValue().forEach(messages::add);
        assertEquals(Set.of(first, second), new HashSet<>(messages.stream().map(Notification::getPatient).toList()));
        for (Notification message : messages) {
            assertSame(message.getPatient(), message.getAppointment().getPatient());
            assertEquals("DOCTOR_DELAY", message.getType());
            assertNull(message.getStaff());
            assertFalse(message.getIsRead());
            assertEquals(LocalDateTime.now(clock), message.getCreatedAt());
            assertTrue(message.getMessage().contains("Ada Doctor"));
            assertTrue(message.getMessage().contains("2030-01-01 10:00–11:00"));
            assertTrue(message.getMessage().contains("30 minutes"));
            assertTrue(message.getMessage().contains("Please arrive later"));
            assertFalse(message.getMessage().contains(first.getPatientEmail()));
            assertFalse(message.getMessage().contains(first.getPatientName()));
        }
        assertEquals(AvailabilityStatus.UNAVAILABLE, doctor.getAvailabilityStatus());
        assertEquals(SlotStatus.OPEN, slot.getSlotStatus());
        verify(staff, never()).save(any());
        verify(schedules, never()).save(any());
        verify(appointments, never()).save(any());
    }

    @Test
    void adminCanSendAbsenceWithFull500CharacterMessageWithoutCancellationOrAvailabilityMutation() {
        admin();
        var appointment = appointment(patient(), AppointmentStatus.BOOKED);
        when(appointments.findBroadcastRecipients(any(), anyCollection())).thenReturn(List.of(appointment));
        String custom = "x".repeat(500);
        service.broadcast(jwt, slot.getSlotId(), new ScheduleBroadcastRequest(ScheduleBroadcastRequest.Type.ABSENCE, custom, null));
        verify(notifications).saveAll(argThat(messages -> {
            var notification = messages.iterator().next();
            return "DOCTOR_ABSENCE".equals(notification.getType()) && notification.getMessage().endsWith(custom)
                    && notification.getMessage().length() > 500;
        }));
        assertEquals(AppointmentStatus.BOOKED, appointment.getAppointmentStatus());
        assertEquals(AvailabilityStatus.UNAVAILABLE, doctor.getAvailabilityStatus());
    }

    @Test
    void noRecipientsReturnsZeroAndStillAuditsWithoutExposingPatients() {
        actor(doctor);
        assertEquals(0, service.broadcast(jwt, slot.getSlotId(), delay).recipientCount());
        verify(notifications).saveAll(List.of());
        verify(audit).record("SCHEDULE_BROADCAST", "DELAY: 0 recipients", "Schedule", slot.getSlotId(), jwt);
    }

    @Test
    void serviceRejectsOtherDoctorNursePatientAndInactiveActors() {
        List<Staff> actors = new ArrayList<>(); actors.add(null);
        for (StaffRole role : StaffRole.values()) {
            actors.add(profile(role, false));
            if (role != StaffRole.ADMIN) actors.add(profile(role, true));
        }
        for (Staff denied : actors) {
            actor(denied);
            assertStatus(HttpStatus.FORBIDDEN, () -> service.broadcast(jwt, slot.getSlotId(), delay));
        }
        verifyNoInteractions(appointments, notifications, audit);
    }

    @Test
    void validatesNonblankAndBoundedMessageAndExactDelayRules() {
        actor(doctor);
        List<ScheduleBroadcastRequest> invalid = new ArrayList<>();
        invalid.add(null);
        invalid.add(new ScheduleBroadcastRequest(null, "test", 1));
        for (String message : Arrays.asList(null, "", " \n\t", "x".repeat(501))) {
            invalid.add(new ScheduleBroadcastRequest(ScheduleBroadcastRequest.Type.DELAY, message, 1));
        }
        for (Number minutes : Arrays.<Number>asList(null, -1, 0, 1441, 1.5, 1.0, Long.MAX_VALUE)) {
            invalid.add(new ScheduleBroadcastRequest(ScheduleBroadcastRequest.Type.DELAY, "test", minutes));
        }
        invalid.add(new ScheduleBroadcastRequest(ScheduleBroadcastRequest.Type.ABSENCE, "test", 1));
        for (var request : invalid) assertStatus(HttpStatus.BAD_REQUEST, () -> service.broadcast(jwt, slot.getSlotId(), request));
        verifyNoInteractions(schedules, appointments, notifications, audit);
    }

    @Test
    void maximumDelayIsAcceptedAndMissingScheduleReturns404() {
        actor(doctor);
        service.broadcast(jwt, slot.getSlotId(), new ScheduleBroadcastRequest(ScheduleBroadcastRequest.Type.DELAY, "test", 1440));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.broadcast(jwt, UUID.randomUUID(), delay));
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "notification", "audit"})
    void broadcastSaveAndAuditShareCommitOrRollback(String failure) {
        actor(doctor);
        when(appointments.findBroadcastRecipients(any(), anyCollection())).thenReturn(List.of(appointment(patient(), AppointmentStatus.WAITING)));
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(call -> {
            assertFalse(call.<TransactionDefinition>getArgument(0).isReadOnly());
            return new SimpleTransactionStatus();
        });
        if (failure.equals("notification")) when(notifications.saveAll(any())).thenThrow(new IllegalStateException());
        if (failure.equals("audit")) doThrow(new IllegalStateException()).when(audit).record(anyString(), anyString(), anyString(), any(), any());
        var proxy = proxy(service, transactions);
        if (failure.equals("none")) {
            proxy.broadcast(jwt, slot.getSlotId(), delay);
            var order = inOrder(notifications, audit, transactions);
            order.verify(notifications).saveAll(any());
            order.verify(audit).record(anyString(), anyString(), anyString(), any(), eq(jwt));
            order.verify(transactions).commit(any());
            verify(transactions, never()).rollback(any());
        } else {
            assertThrows(IllegalStateException.class, () -> proxy.broadcast(jwt, slot.getSlotId(), delay));
            verify(transactions).rollback(any());
            verify(transactions, never()).commit(any());
        }
    }
}
