package com.cliniq.backend.admin;

import com.cliniq.backend.dto.*;
import com.cliniq.backend.enums.*;
import com.cliniq.backend.model.Schedule;
import com.cliniq.backend.service.ScheduleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminScheduleServiceTest extends AdminFixture {
    final ScheduleService service = new ScheduleService(schedules, appointments, staff, audit, policy, clock);

    AdminScheduleRequest request() { return request(3, null); }
    AdminScheduleRequest request(Number capacity, SlotStatus status) {
        return new AdminScheduleRequest(doctor.getStaffId(), slot.getSlotDate(), slot.getStartTime(), slot.getEndTime(), capacity, status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "delete", "create-fail", "update-fail", "delete-fail"})
    void adminScheduleWritesAndAuditShareTransaction(String operation) {
        admin();
        var transactions = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(call -> {
            assertFalse(call.<org.springframework.transaction.TransactionDefinition>getArgument(0).isReadOnly());
            return new org.springframework.transaction.support.SimpleTransactionStatus();
        });
        if (operation.endsWith("fail")) doThrow(new IllegalStateException()).when(audit)
                .record(anyString(), anyString(), anyString(), any(), any());
        var proxy = proxy(service, transactions);
        Runnable action = () -> {
            if (operation.startsWith("create")) proxy.createAdmin(jwt, request());
            else if (operation.startsWith("update")) proxy.updateAdmin(jwt, slot.getSlotId(), request());
            else proxy.deleteAdmin(jwt, slot.getSlotId());
        };
        if (operation.endsWith("fail")) {
            assertThrows(IllegalStateException.class, action::run);
            verify(transactions).rollback(any());
            verify(transactions, never()).commit(any());
        } else {
            action.run();
            var order = inOrder(audit, transactions);
            order.verify(audit).record(anyString(), anyString(), anyString(), any(), eq(jwt));
            order.verify(transactions).commit(any());
            verify(transactions, never()).rollback(any());
        }
    }

    @Test
    void createsUpdatesAndDeletesWithActiveAdminAuditAndLockedHistoryGuard() {
        admin();
        var created = service.createAdmin(jwt, request());
        assertNotNull(created.slotId());
        assertEquals(SlotStatus.OPEN, created.slotStatus());
        assertEquals(3, created.capacity());
        assertEquals(AvailabilityStatus.UNAVAILABLE, created.staff().availabilityStatus());
        verify(audit).record("SCHEDULE_CREATED", "Doctor availability changed", "Schedule", created.slotId(), jwt);
        var updated = service.updateAdmin(jwt, slot.getSlotId(), request(7, SlotStatus.BLOCKED));
        assertEquals(7, updated.capacity());
        assertEquals(SlotStatus.BLOCKED, updated.slotStatus());
        var order = inOrder(schedules, appointments);
        order.verify(schedules).findByIdForUpdate(slot.getSlotId());
        order.verify(appointments).existsBySlot_SlotId(slot.getSlotId());
        order.verify(schedules).save(slot);
        service.deleteAdmin(jwt, slot.getSlotId());
        verify(schedules).delete(slot);
        verify(audit).record("SCHEDULE_UPDATED", "Doctor availability changed", "Schedule", slot.getSlotId(), jwt);
        verify(audit).record("SCHEDULE_DELETED", "Doctor availability changed", "Schedule", slot.getSlotId(), jwt);
    }

    @Test
    void listsSafeDtoIncludingNullableDepartmentAndLegacyCapacity() {
        admin();
        doctor.setDepartment(null);
        slot.setCapacity(null);
        when(schedules.findAllForAdmin()).thenReturn(List.of(slot));
        var result = service.listAdmin(jwt).getFirst();
        assertNull(result.staff().department());
        assertEquals(1, result.capacity());
    }

    @Test
    void allAdminMethodsEnforceDatabaseAdminRoleIncludingInactiveProfilesAndPatients() {
        List<com.cliniq.backend.model.Staff> denied = new ArrayList<>();
        denied.add(null);
        for (StaffRole role : StaffRole.values()) {
            denied.add(profile(role, false));
            if (role != StaffRole.ADMIN) denied.add(profile(role, true));
        }
        for (var profile : denied) {
            actor(profile);
            assertStatus(HttpStatus.FORBIDDEN, () -> service.listAdmin(jwt));
            assertStatus(HttpStatus.FORBIDDEN, () -> service.createAdmin(jwt, request()));
            assertStatus(HttpStatus.FORBIDDEN, () -> service.updateAdmin(jwt, slot.getSlotId(), request()));
            assertStatus(HttpStatus.FORBIDDEN, () -> service.deleteAdmin(jwt, slot.getSlotId()));
        }
        verifyNoInteractions(schedules, appointments, audit);
    }

    @ParameterizedTest
    @EnumSource(AppointmentStatus.class)
    void anyLinkedHistoryPreventsUpdateAndDeletionEvenCancelled(AppointmentStatus historyStatus) {
        admin();
        var history = appointment(patient(), historyStatus);
        when(appointments.existsBySlot_SlotId(history.getSlot().getSlotId())).thenReturn(true);
        assertStatus(HttpStatus.CONFLICT, () -> service.updateAdmin(jwt, slot.getSlotId(), request()));
        assertStatus(HttpStatus.CONFLICT, () -> service.deleteAdmin(jwt, slot.getSlotId()));
        verify(schedules, never()).save(any());
        verify(schedules, never()).delete(any());
    }

    @Test
    void bookedStatusCannotBeForgedOrMutatedEvenWithNoLinkedAppointment() {
        admin();
        assertStatus(HttpStatus.CONFLICT, () -> service.createAdmin(jwt, request(3, SlotStatus.BOOKED)));
        assertStatus(HttpStatus.CONFLICT, () -> service.updateAdmin(jwt, slot.getSlotId(), request(3, SlotStatus.BOOKED)));
        slot.setSlotStatus(SlotStatus.BOOKED);
        assertStatus(HttpStatus.CONFLICT, () -> service.deleteAdmin(jwt, slot.getSlotId()));
        verify(schedules, never()).save(any());
    }

    @Test
    void rejectsNonDoctorInactiveAndDepartmentlessTargets() {
        admin();
        for (StaffRole role : StaffRole.values()) {
            if (role == StaffRole.DOCTOR) continue;
            doctor.setStaffRole(role);
            assertStatus(HttpStatus.BAD_REQUEST, () -> service.createAdmin(jwt, request()));
        }
        doctor.setStaffRole(StaffRole.DOCTOR);
        doctor.setActive(false);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createAdmin(jwt, request()));
        doctor.setActive(true); doctor.setDepartment(null);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createAdmin(jwt, request()));
        verify(schedules, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0, 10001})
    void rejectsInvalidCapacity(Integer capacity) {
        admin();
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createAdmin(jwt, request(capacity, null)));
        verify(schedules, never()).save(any());
    }

    @Test
    void rejectsFractionalAndOverflowCapacityAndMissingDoctor() {
        admin();
        for (Number capacity : List.of(1.5, 1.0, Long.MAX_VALUE)) {
            assertStatus(HttpStatus.BAD_REQUEST, () -> service.createAdmin(jwt, request(capacity, null)));
        }
        var noDoctor = new AdminScheduleRequest(null, slot.getSlotDate(), slot.getStartTime(), slot.getEndTime(), 3, null);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createAdmin(jwt, noDoctor));
        when(staff.findById(doctor.getStaffId())).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.createAdmin(jwt, request()));
    }

    @Test
    void validatesTimesAndFutureStartUsingClinicZoneNotUtcOrServerZone() {
        admin();
        // Clock is 07:00 UTC / 09:00 clinic time. Exactly 09:00 is no longer future.
        for (LocalTime start : List.of(LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(11, 0), LocalTime.of(12, 0))) {
            var invalid = new AdminScheduleRequest(doctor.getStaffId(), slot.getSlotDate(), start, LocalTime.of(11, 0), 1, null);
            assertStatus(HttpStatus.BAD_REQUEST, () -> service.createAdmin(jwt, invalid));
        }
        slot.setSlotDate(slot.getSlotDate().minusDays(1));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createAdmin(jwt, request()));
        slot.setSlotDate(null);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createAdmin(jwt, request()));
    }

    @Test
    void rejectsAmbiguousAndNonexistentClinicLocalTimes() {
        admin();
        var dstClock = Clock.fixed(Instant.parse("2029-01-01T00:00:00Z"), ZoneId.of("America/New_York"));
        var dstService = new ScheduleService(schedules, appointments, staff, audit, policy, dstClock);
        for (LocalDate date : List.of(LocalDate.of(2030, 3, 10), LocalDate.of(2030, 11, 3))) {
            LocalTime start = date.getMonthValue() == 3 ? LocalTime.of(2, 30) : LocalTime.of(1, 30);
            var invalid = new AdminScheduleRequest(doctor.getStaffId(), date, start, LocalTime.of(4, 0), 1, null);
            assertStatus(HttpStatus.BAD_REQUEST, () -> dstService.createAdmin(jwt, invalid));
        }
    }

    @Test
    void selfSchedulingDerivesOwnerAndPreservesNullCapacityLegacyDefault() {
        actor(doctor);
        Schedule input = new Schedule();
        input.setSlotId(UUID.randomUUID()); input.setStaff(profile(StaffRole.ADMIN, true));
        input.setSlotDate(slot.getSlotDate()); input.setStartTime(slot.getStartTime()); input.setEndTime(slot.getEndTime());
        input.setSlotStatus(SlotStatus.BOOKED);
        Schedule saved = service.createForStaff(jwt, input);
        assertSame(doctor, saved.getStaff());
        assertEquals(SlotStatus.OPEN, saved.getSlotStatus());
        assertEquals(1, saved.effectiveCapacity());
    }

    @Test
    void genericHttpMutationsCannotBypassOwnershipOrDoctorTargetRules() {
        actor(profile(StaffRole.NURSE, true));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.createForStaff(jwt, new Schedule()));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.updateSchedule(slot.getSlotId(), new Schedule(), jwt));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.deleteSchedule(slot.getSlotId(), jwt));
        actor(profile(StaffRole.DOCTOR, true));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.updateSchedule(slot.getSlotId(), new Schedule(), jwt));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.deleteSchedule(slot.getSlotId(), jwt));
        actor(doctor);
        Schedule details = new Schedule(); details.setStaff(profile(StaffRole.DOCTOR, true));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.updateSchedule(slot.getSlotId(), details, jwt));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.updateSchedule(slot.getSlotId(), details, null));
        verify(schedules, never()).save(any());
        verify(schedules, never()).delete(any());
    }
}
