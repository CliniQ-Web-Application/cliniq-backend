package com.cliniq.backend.operational;

import com.cliniq.backend.dto.QueuePositionResponse;
import com.cliniq.backend.enums.*;
import com.cliniq.backend.model.Queue;
import com.cliniq.backend.service.OperationalService;
import com.cliniq.backend.service.QueueOrdering;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueuePositionTest extends OperationalFixture {
    private QueuePositionResponse snapshot(Queue entry) {
        return service.queuePosition(jwt, entry.getAppointment().getAppointmentId());
    }

    @Test
    void queueAdvancesAfterCallAndCompletionWithoutUpdatingStoredPositions() {
        Queue first = entry(1, AppointmentStatus.WAITING);
        Queue second = entry(2, AppointmentStatus.WAITING);
        assertEquals(2, snapshot(second).queuePosition());
        assertEquals(15, snapshot(second).estimatedWaitMinutes());
        assertSame(first.getAppointment(), service.callNext(jwt));
        assertEquals(1, snapshot(second).queuePosition());
        assertEquals(15, snapshot(second).estimatedWaitMinutes(), "Occupied consultation is not advertised as zero wait");
        assertNull(snapshot(first).queuePosition());
        assertEquals(0, snapshot(first).estimatedWaitMinutes());
        service.complete(jwt, first.getAppointment().getAppointmentId());
        assertEquals(1, snapshot(second).queuePosition());
        assertEquals(0, snapshot(second).estimatedWaitMinutes());
        assertEquals(AppointmentStatus.COMPLETED, snapshot(first).queueStatus());
        assertNull(snapshot(first).queuePosition());
        assertEquals(0, snapshot(first).estimatedWaitMinutes());
        assertEquals(99, first.getPosition());
        assertEquals(99, second.getPosition());
        verify(queues, never()).save(any());
    }

    @Test
    void urgentFirstThenBookingTimeThenStableIdMatchesCallNextEvenWithShuffledInput() {
        Queue lateNormal = entry(4, AppointmentStatus.WAITING);
        Queue normal = entry(3, AppointmentStatus.WAITING);
        Queue urgentHighId = entry(2, AppointmentStatus.WAITING);
        Queue urgentLowId = entry(1, AppointmentStatus.WAITING);
        lateNormal.getAppointment().setBookedAt(moment.plusMinutes(10));
        urgentHighId.getAppointment().setBookedAt(moment.plusHours(1));
        urgentLowId.getAppointment().setBookedAt(moment.plusHours(1));
        urgentHighId.getAppointment().setPriority(Priority.URGENT);
        urgentLowId.getAppointment().setPriority(Priority.URGENT);
        List<Queue> expected = List.of(urgentLowId, urgentHighId, normal, lateNormal);
        for (int index = 0; index < expected.size(); index++) {
            assertEquals(index + 1, snapshot(expected.get(index)).queuePosition());
        }
        for (Queue next : expected) {
            assertEquals(1, snapshot(next).queuePosition());
            assertSame(next.getAppointment(), service.callNext(jwt));
        }
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.callNext(jwt)).getStatusCode());
    }

    @Test
    void handlesLegacyNullPriorityAndBookingCheckInCreationFallbacksDeterministically() {
        Queue missing = entry(5, AppointmentStatus.WAITING);
        missing.getAppointment().setPriority(null);
        missing.getAppointment().setBookedAt(null);
        Queue created = entry(4, AppointmentStatus.WAITING);
        created.getAppointment().setBookedAt(null);
        created.getAppointment().setCreatedAt(moment.plusMinutes(2));
        Queue checkedIn = entry(3, AppointmentStatus.WAITING);
        checkedIn.getAppointment().setBookedAt(null);
        checkedIn.getAppointment().setCheckInTime(moment.plusMinutes(1));
        Queue booked = entry(2, AppointmentStatus.WAITING);
        booked.getAppointment().setCheckInTime(moment.minusDays(1));
        Queue urgent = entry(1, AppointmentStatus.WAITING);
        urgent.getAppointment().setBookedAt(null);
        urgent.getAppointment().setPriority(Priority.URGENT);
        List<Queue> expected = List.of(urgent, booked, checkedIn, created, missing);
        assertEquals(expected, entries.stream().sorted(QueueOrdering.NEXT_PATIENT).toList());
        for (Queue next : expected) {
            assertEquals(1, snapshot(next).queuePosition());
            assertSame(next.getAppointment(), service.callNext(jwt));
        }
        // Missing legacy IDs are also safe for sorting, with queue ID as the final tie-breaker.
        missing.getAppointment().setAppointmentId(null);
        created.getAppointment().setAppointmentId(null);
        created.getAppointment().setCreatedAt(null);
        assertDoesNotThrow(() -> QueueOrdering.NEXT_PATIENT.compare(missing, created));
    }

    @Test
    void excludesOtherDepartmentsAndAllNonWaitingStatusesEvenWithDifferentStaffDepartment() {
        for (AppointmentStatus status : AppointmentStatus.values()) {
            if (status != AppointmentStatus.WAITING) entry(10 + status.ordinal(), status);
        }
        Queue otherDepartment = entry(1, AppointmentStatus.WAITING);
        otherDepartment.setDepartment(department());
        Queue target = entry(2, AppointmentStatus.WAITING);
        // Queue membership, not the doctor's department, defines the snapshot department.
        doctor.setDepartment(otherDepartment.getDepartment());
        var response = snapshot(target);
        assertEquals(1, response.queuePosition());
        assertEquals(15, response.estimatedWaitMinutes());
        verify(queues).findByDepartmentAndAppointmentStatus(department, AppointmentStatus.WAITING);
        verify(queues).averageCompletedConsultationSeconds(department);
        assertSame(otherDepartment.getAppointment(), service.callNext(jwt));
    }

    @Test
    void usesDatabaseDepartmentAverageAndRoundsUpToWholeMinutes() {
        entry(1, AppointmentStatus.WAITING);
        Queue target = entry(2, AppointmentStatus.WAITING);
        // Database mean of 10- and 21-minute completed consultations, returned in seconds.
        when(queues.averageCompletedConsultationSeconds(department)).thenReturn(930.0);
        assertEquals(16, snapshot(target).estimatedWaitMinutes());
        entry(3, AppointmentStatus.INCONSULTATION);
        assertEquals(31, snapshot(target).estimatedWaitMinutes());
    }

    @Test
    void absentOrNonpositiveAggregateFallsBackAndPositiveSubSecondAverageIsNotDiscarded() {
        entry(1, AppointmentStatus.WAITING);
        Queue target = entry(2, AppointmentStatus.WAITING);
        for (Double average : new Double[]{null, 0.0, -1.0}) {
            when(queues.averageCompletedConsultationSeconds(department)).thenReturn(average);
            assertEquals(15, snapshot(target).estimatedWaitMinutes());
        }
        when(queues.averageCompletedConsultationSeconds(department)).thenReturn(0.5);
        assertEquals(1, snapshot(target).estimatedWaitMinutes());
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = "WAITING", mode = EnumSource.Mode.EXCLUDE)
    void nonWaitingQueuedRecordsHaveNoPositionAndOnlyConsultationOrTerminalHaveZeroEta(AppointmentStatus status) {
        Queue target = entry(1, status);
        var response = snapshot(target);
        assertNull(response.queuePosition());
        assertEquals(status, response.queueStatus());
        assertEquals(status == AppointmentStatus.BOOKED || status == AppointmentStatus.CHECKEDIN ? null : 0,
                response.estimatedWaitMinutes());
        verify(queues, never()).averageCompletedConsultationSeconds(any());
        verify(queues, never()).findByDepartmentAndAppointmentStatus(any(), any());
    }

    @Test
    void ownershipMissingAppointmentAndUnqueuedRecordsHaveExpectedErrorsWithoutWrites() {
        Queue target = entry(1, AppointmentStatus.WAITING);
        UUID id = target.getAppointment().getAppointmentId();
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.queuePosition(jwt(UUID.randomUUID()), id)).getStatusCode());
        verify(queues, never()).findByAppointment(any());
        when(queues.findByAppointment(target.getAppointment())).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.queuePosition(jwt, id)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.queuePosition(jwt, UUID.randomUUID())).getStatusCode());
        verify(appointments, never()).save(any());
        verify(queues, never()).save(any());
        verifyNoInteractions(schedules, audit, notifications);
    }

    @Test
    void snapshotIsReadOnlyReturnsOnlyDtoAndDoesNotMutateOrPersist() throws Exception {
        Queue target = entry(1, AppointmentStatus.WAITING);
        var before = target.getAppointment().getAppointmentStatus();
        var response = snapshot(target);
        assertEquals(target.getAppointment().getAppointmentId(), response.appointmentId());
        assertEquals(1, response.queuePosition());
        assertEquals(99, target.getPosition());
        assertEquals(before, target.getAppointment().getAppointmentStatus());
        assertTrue(OperationalService.class.getMethod("queuePosition", org.springframework.security.oauth2.jwt.Jwt.class, UUID.class)
                .getAnnotation(Transactional.class).readOnly());
        verify(queues, never()).save(any());
        verify(queues, never()).flush();
        verify(appointments, never()).save(any());
        verify(appointments, never()).saveAndFlush(any());
        verifyNoInteractions(schedules, audit, notifications);
    }

    @Test
    void invalidPrincipalIsRejectedBeforeRepositoryReads() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> service.queuePosition(null, UUID.randomUUID())).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> service.cancelPatient(null, UUID.randomUUID())).getStatusCode());
        verifyNoInteractions(appointments, queues, schedules);
    }
}
