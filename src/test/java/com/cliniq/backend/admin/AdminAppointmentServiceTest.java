package com.cliniq.backend.admin;

import com.cliniq.backend.dto.*;
import com.cliniq.backend.enums.*;
import com.cliniq.backend.service.*;
import com.cliniq.backend.model.Department;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAppointmentServiceTest extends AdminFixture {
    final OperationalService operations = mock(OperationalService.class);
    final AdminAppointmentService service = new AdminAppointmentService(appointments, policy, operations, queues);

    @Test
    void delegatesAllFiltersAndPagingToDatabaseWithLiteralCaseInsensitiveSearchAndStablePageMetadata() {
        admin();
        var appointment = appointment(patient(), AppointmentStatus.BOOKED);
        LocalDate from = LocalDate.of(2030, 1, 1), to = LocalDate.of(2030, 1, 31);
        when(appointments.findAdminAppointments(from, to, AppointmentStatus.BOOKED, department.getDepartmentId(), doctor.getStaffId(),
                "%grace!_100!%!!%", PageRequest.of(2, 5))).thenReturn(new PageImpl<>(List.of(appointment), PageRequest.of(2, 5), 26));
        var result = service.list(jwt, from, to, AppointmentStatus.BOOKED, department.getDepartmentId(), doctor.getStaffId(), " GRACE_100%! ", 2, 5);
        assertEquals(2, result.page()); assertEquals(5, result.size());
        assertEquals(26, result.totalElements()); assertEquals(6, result.totalPages());
        var item = result.items().getFirst();
        assertEquals("Grace Patient", item.patientName());
        assertEquals("grace@example.com", item.patientEmail());
        assertEquals("71234567", item.patientPhone());
        assertEquals("Ada Doctor", item.doctorName());
        assertEquals(doctor.getStaffId(), item.doctorId());
        assertEquals(slot.getSlotId(), item.slotId());
        assertEquals(department.getName(), item.departmentName());
        verify(appointments, never()).findAll();
        verifyNoInteractions(queues);
    }

    @Test
    void optionalFiltersAndBlankSearchRemainNullAndEmptyPagesPreserveCounts() {
        admin();
        when(appointments.findAdminAppointments(null, null, null, null, null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        var result = service.list(jwt, null, null, null, null, null, "  ", 0, 20);
        assertEquals(List.of(), result.items()); assertEquals(0, result.totalElements()); assertEquals(0, result.totalPages());
        verifyNoInteractions(queues);
    }

    @Test
    void inspectAndCancellationReturnSameFlatDtoAndCancellationUsesSharedOperationalCore() {
        admin();
        var appointment = appointment(patient(), AppointmentStatus.BOOKED);
        when(appointments.findById(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        assertEquals(AdminAppointment.from(appointment), service.get(jwt, appointment.getAppointmentId()));
        when(operations.cancelAdmin(jwt, appointment.getAppointmentId())).thenAnswer(call -> {
            appointment.setAppointmentStatus(AppointmentStatus.CANCELLED); return appointment;
        });
        assertEquals(AppointmentStatus.CANCELLED, service.cancel(jwt, appointment.getAppointmentId()).appointmentStatus());
        verify(operations).cancelAdmin(jwt, appointment.getAppointmentId());
        verify(appointments, never()).save(any());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.get(jwt, UUID.randomUUID()));
    }

    @Test
    void legacyWalkInsAndMissingRelationshipsAreNullSafeWithoutInventingScheduleOrDoctor() {
        admin();
        var walkIn = appointment(patient(), AppointmentStatus.WAITING);
        walkIn.setSlot(null); walkIn.setStaff(null); walkIn.setPatient(null);
        when(appointments.findById(walkIn.getAppointmentId())).thenReturn(Optional.of(walkIn));
        var result = service.get(jwt, walkIn.getAppointmentId());
        assertNull(result.slotId()); assertNull(result.slotDate()); assertNull(result.startTime()); assertNull(result.endTime());
        assertNull(result.doctorId()); assertNull(result.doctorName()); assertNull(result.departmentId()); assertNull(result.departmentName());
        assertNull(result.patientId()); assertNull(result.patientName()); assertNull(result.patientEmail()); assertNull(result.patientPhone());
        doctor.setStaffRole(StaffRole.NURSE); walkIn.setStaff(doctor);
        assertNull(service.get(jwt, walkIn.getAppointmentId()).doctorId());
        assertEquals(department.getDepartmentId(), service.get(jwt, walkIn.getAppointmentId()).departmentId());
    }

    @ParameterizedTest
    @EnumSource(StaffRole.class)
    void slotlessAppointmentUsesTreatmentNotRegistrarDepartmentConsistentlyOnListDetailAndCancel(StaffRole registrarRole) {
        admin();
        var registrar = profile(registrarRole, true);
        registrar.setDepartment(department);
        var appointment = appointment(patient(), AppointmentStatus.BOOKED);
        appointment.setSlot(null);
        appointment.setStaff(registrar);
        Department treatment = new Department();
        treatment.setDepartmentId(UUID.randomUUID()); treatment.setName("Treatment department");
        when(queues.findAdminDepartmentsByAppointmentIds(List.of(appointment.getAppointmentId())))
                .thenReturn(Collections.singletonList(new Object[]{appointment.getAppointmentId(), treatment}));
        when(appointments.findAdminAppointments(null, null, null, treatment.getDepartmentId(), null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(appointment), PageRequest.of(0, 20), 1));
        when(appointments.findById(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        when(operations.cancelAdmin(jwt, appointment.getAppointmentId())).thenAnswer(call -> {
            appointment.setAppointmentStatus(AppointmentStatus.CANCELLED); return appointment;
        });
        var listed = service.list(jwt, null, null, null, treatment.getDepartmentId(), null, null, 0, 20).items().getFirst();
        var inspected = service.get(jwt, appointment.getAppointmentId());
        var cancelled = service.cancel(jwt, appointment.getAppointmentId());
        for (var item : List.of(listed, inspected, cancelled)) {
            assertEquals(treatment.getDepartmentId(), item.departmentId());
            assertEquals(treatment.getName(), item.departmentName());
            assertEquals(registrarRole == StaffRole.DOCTOR ? registrar.getStaffId() : null, item.doctorId());
            assertNull(item.slotId());
        }
        assertEquals(AppointmentStatus.CANCELLED, cancelled.appointmentStatus());
        assertSame(department, registrar.getDepartment());
        verify(queues, times(3)).findAdminDepartmentsByAppointmentIds(List.of(appointment.getAppointmentId()));
        verify(queues, never()).findByAppointment(any());
    }

    @Test
    void resolvesOnlyCurrentPageSlotlessDepartmentsInOneBatchWithNoQueueAndNullDepartmentDistinct() {
        admin();
        var scheduled = appointment(patient(), AppointmentStatus.BOOKED);
        var queued = appointment(patient(), AppointmentStatus.WAITING); queued.setSlot(null);
        var noRegistrar = appointment(patient(), AppointmentStatus.WAITING); noRegistrar.setSlot(null); noRegistrar.setStaff(null);
        var noQueue = appointment(patient(), AppointmentStatus.WAITING); noQueue.setSlot(null);
        var nullQueueDepartment = appointment(patient(), AppointmentStatus.WAITING); nullQueueDepartment.setSlot(null);
        List<UUID> ids = List.of(queued.getAppointmentId(), noRegistrar.getAppointmentId(), noQueue.getAppointmentId(), nullQueueDepartment.getAppointmentId());
        Department treatment = new Department(); treatment.setDepartmentId(UUID.randomUUID()); treatment.setName("Treatment");
        when(queues.findAdminDepartmentsByAppointmentIds(ids)).thenReturn(List.of(
                new Object[]{queued.getAppointmentId(), treatment}, new Object[]{noRegistrar.getAppointmentId(), treatment},
                new Object[]{nullQueueDepartment.getAppointmentId(), null}));
        when(appointments.findAdminAppointments(null, null, null, null, null, null, PageRequest.of(1, 5)))
                .thenReturn(new PageImpl<>(List.of(scheduled, queued, noRegistrar, noQueue, nullQueueDepartment), PageRequest.of(1, 5), 19));
        var page = service.list(jwt, null, null, null, null, null, null, 1, 5);
        assertEquals(19, page.totalElements()); assertEquals(4, page.totalPages()); assertEquals(5, page.items().size());
        assertEquals(department.getDepartmentId(), page.items().get(0).departmentId());
        assertEquals(treatment.getDepartmentId(), page.items().get(1).departmentId());
        assertEquals(treatment.getDepartmentId(), page.items().get(2).departmentId());
        assertNull(page.items().get(2).doctorId());
        assertEquals(department.getDepartmentId(), page.items().get(3).departmentId());
        assertNull(page.items().get(4).departmentId()); assertNull(page.items().get(4).departmentName());
        verify(queues).findAdminDepartmentsByAppointmentIds(ids);
        verifyNoMoreInteractions(queues);
        verify(appointments, never()).findAll();
    }

    @Test
    void allOperationsRejectPatientsOtherStaffAndInactiveAdminsBeforeDataAccess() {
        List<com.cliniq.backend.model.Staff> denied = new ArrayList<>(); denied.add(null);
        for (StaffRole role : StaffRole.values()) {
            denied.add(profile(role, false));
            if (role != StaffRole.ADMIN) denied.add(profile(role, true));
        }
        for (var profile : denied) {
            actor(profile);
            assertStatus(HttpStatus.FORBIDDEN, () -> service.list(jwt, null, null, null, null, null, null, 0, 20));
            assertStatus(HttpStatus.FORBIDDEN, () -> service.get(jwt, UUID.randomUUID()));
            assertStatus(HttpStatus.FORBIDDEN, () -> service.cancel(jwt, UUID.randomUUID()));
        }
        verifyNoInteractions(appointments, operations, queues);
    }

    @Test
    void rejectsInvalidPageSizeOrDateRangeBeforeQuery() {
        admin();
        for (int size : new int[]{-1, 0, 101, Integer.MAX_VALUE}) {
            assertStatus(HttpStatus.BAD_REQUEST, () -> service.list(jwt, null, null, null, null, null, null, 0, size));
        }
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.list(jwt, null, null, null, null, null, null, -1, 20));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.list(jwt, LocalDate.of(2030, 2, 1), LocalDate.of(2030, 1, 1), null, null, null, null, 0, 20));
        verifyNoInteractions(appointments, queues);
    }

    @Test
    void conversionIsInsideReadTransactionsAndCancellationIsAWriteTransaction() throws Exception {
        assertTrue(AdminAppointmentService.class.getAnnotation(Transactional.class).readOnly());
        assertFalse(AdminAppointmentService.class.getMethod("cancel", org.springframework.security.oauth2.jwt.Jwt.class, UUID.class)
                .getAnnotation(Transactional.class).readOnly());
    }
}
