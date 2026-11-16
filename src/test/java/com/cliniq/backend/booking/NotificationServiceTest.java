package com.cliniq.backend.booking;

import com.cliniq.backend.dto.NotificationResponse;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Notification;
import com.cliniq.backend.model.Patient;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.NotificationRepository;
import com.cliniq.backend.repository.PatientRepository;
import com.cliniq.backend.repository.StaffRepository;
import com.cliniq.backend.service.AuditLogsService;
import com.cliniq.backend.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final PatientRepository patients = mock(PatientRepository.class);
    private final AuditLogsService audit = mock(AuditLogsService.class);
    private final NotificationService service = new NotificationService(patients, staff, notifications, audit);
    private final UUID userId = UUID.randomUUID();
    private Notification notification;
    private Patient recipientPatient;
    private Staff recipientStaff;

    @BeforeEach
    void setUp() {
        recipientPatient = new Patient();
        recipientPatient.setAuthUserId(userId);
        recipientStaff = new Staff();
        recipientStaff.setAuthUserId(userId);
        notification = new Notification();
        notification.setNotificationId(UUID.randomUUID());
        notification.setType("NEW_BOOKING");
        notification.setMessage("An appointment was booked");
        notification.setCreatedAt(LocalDateTime.of(2030, 1, 1, 9, 0));
        notification.setIsRead(false);
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(UUID.randomUUID());
        notification.setAppointment(appointment);
        when(notifications.findById(notification.getNotificationId())).thenReturn(Optional.of(notification));
        when(notifications.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private void selectRecipient(boolean staffAccount) {
        if (staffAccount) {
            when(staff.findByAuthUserId(userId)).thenReturn(Optional.of(recipientStaff));
            notification.setStaff(recipientStaff);
        } else {
            notification.setPatient(recipientPatient);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void marksDirectRecipientsNotificationReadWithoutChangingOtherFields(boolean staffAccount) {
        selectRecipient(staffAccount);
        NotificationResponse before = NotificationResponse.from(notification);
        Appointment appointment = notification.getAppointment();

        NotificationResponse result = service.markAsRead(userId, notification.getNotificationId());

        assertTrue(result.isRead());
        assertEquals(new NotificationResponse(before.notificationId(), before.type(), before.message(), true,
                before.createdAt(), before.appointmentId()), result);
        assertSame(appointment, notification.getAppointment());
        assertSame(staffAccount ? recipientStaff : null, notification.getStaff());
        assertSame(staffAccount ? null : recipientPatient, notification.getPatient());
        verify(notifications).save(notification);
        verifyNoInteractions(patients, audit);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void repeatedReadIsIdempotentAndDoesNotWriteAgain(boolean staffAccount) {
        selectRecipient(staffAccount);
        var first = service.markAsRead(userId, notification.getNotificationId());
        var second = service.markAsRead(userId, notification.getNotificationId());
        assertEquals(first, second);
        assertTrue(second.isRead());
        verify(notifications, times(1)).save(notification);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsOtherRecipientsWithoutMutatingOrSaving(boolean staffAccount) {
        selectRecipient(staffAccount);
        recipientPatient.setAuthUserId(UUID.randomUUID());
        recipientStaff.setAuthUserId(UUID.randomUUID());
        assertForbiddenWithoutWrite();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void appointmentParticipationAloneDoesNotGrantOwnership(boolean staffAccount) {
        selectRecipient(staffAccount);
        notification.getAppointment().setPatient(recipientPatient);
        notification.getAppointment().setStaff(recipientStaff);
        notification.setPatient(null);
        notification.setStaff(null);
        assertForbiddenWithoutWrite();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void staffCannotFallBackToPatientOwnershipEvenWhenInactive(boolean active) {
        recipientStaff.setActive(active);
        when(staff.findByAuthUserId(userId)).thenReturn(Optional.of(recipientStaff));
        notification.setPatient(recipientPatient);
        assertForbiddenWithoutWrite();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nullRecipientAuthUserIdDoesNotGrantOwnership(boolean staffAccount) {
        selectRecipient(staffAccount);
        recipientPatient.setAuthUserId(null);
        recipientStaff.setAuthUserId(null);
        assertForbiddenWithoutWrite();
    }

    @Test
    void missingNotificationReturns404WithoutWrites() {
        UUID missing = UUID.randomUUID();
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.markAsRead(userId, missing)).getStatusCode());
        verify(notifications, never()).save(any());
        verifyNoInteractions(staff, patients, audit);
    }

    @Test
    void missingIdentityReturns401BeforeRepositoryAccess() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> service.markAsRead(null, notification.getNotificationId())).getStatusCode());
        verifyNoInteractions(notifications, staff, patients, audit);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void listUsesTheSameStaffFirstRecipientSelection(boolean staffAccount) {
        selectRecipient(staffAccount);
        if (staffAccount) {
            // An identity with both profiles must still only see its staff inbox.
            notification.setPatient(recipientPatient);
            when(notifications.findByStaff_AuthUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(notification));
        } else {
            when(notifications.findByPatient_AuthUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(notification));
        }
        assertEquals(List.of(NotificationResponse.from(notification)), service.getMyNotifications(userId));
        if (staffAccount) {
            verify(notifications, never()).findByPatient_AuthUserIdOrderByCreatedAtDesc(any());
        } else {
            verify(notifications, never()).findByStaff_AuthUserIdOrderByCreatedAtDesc(any());
        }
        verify(notifications, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void resolvesAndConvertsDtoInsideReadOrWriteTransaction(boolean readOnly) {
        selectRecipient(false);
        AtomicBoolean active = new AtomicBoolean();
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(call -> {
            TransactionDefinition definition = call.getArgument(0);
            assertEquals(readOnly, definition.isReadOnly());
            active.set(true);
            return new SimpleTransactionStatus();
        });
        doAnswer(call -> {
            assertTrue(active.getAndSet(false));
            return null;
        }).when(transactions).commit(any());
        Appointment lazyAppointment = mock(Appointment.class);
        when(lazyAppointment.getAppointmentId()).thenAnswer(call -> {
            assertTrue(active.get(), "DTO conversion must precede transaction commit");
            return UUID.fromString("c8cce5bc-a75b-46bb-9bc6-b4a9b48dc3e0");
        });
        notification.setAppointment(lazyAppointment);
        when(notifications.findById(notification.getNotificationId())).thenAnswer(call -> {
            assertTrue(active.get());
            return Optional.of(notification);
        });
        when(notifications.findByPatient_AuthUserIdOrderByCreatedAtDesc(userId)).thenAnswer(call -> {
            assertTrue(active.get());
            return List.of(notification);
        });
        when(notifications.save(any())).thenAnswer(call -> {
            assertTrue(active.get());
            return call.getArgument(0);
        });
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        NotificationService proxy = (NotificationService) factory.getProxy();

        NotificationResponse result = readOnly ? proxy.getMyNotifications(userId).getFirst()
                : proxy.markAsRead(userId, notification.getNotificationId());

        assertNotNull(result.appointmentId());
        assertEquals(!readOnly, result.isRead());
        assertFalse(active.get());
        verify(transactions).commit(any());
        verify(transactions, never()).rollback(any());
    }

    private void assertForbiddenWithoutWrite() {
        NotificationResponse before = NotificationResponse.from(notification);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.markAsRead(userId, notification.getNotificationId())).getStatusCode());
        assertEquals(before, NotificationResponse.from(notification));
        verify(notifications, never()).save(any());
        verifyNoInteractions(patients, audit);
    }
}
