package com.cliniq.backend.analytics;

import com.cliniq.backend.dto.AnalyticsResponse;
import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.enums.AvailabilityStatus;
import com.cliniq.backend.enums.StaffRole;
import com.cliniq.backend.model.Department;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.AppointmentRepository;
import com.cliniq.backend.repository.DepartmentRepository;
import com.cliniq.backend.repository.StaffRepository;
import com.cliniq.backend.service.AnalyticsService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final AnalyticsService service = new AnalyticsService(appointments, departments, staff);

    private final LocalDate from = LocalDate.of(2030, 1, 1);
    private final LocalDate to = LocalDate.of(2030, 1, 7);
    private final UUID departmentId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();

    private static Department department(String name) {
        Department result = new Department();
        result.setDepartmentId(UUID.randomUUID());
        result.setName(name);
        return result;
    }

    private static Staff doctor(String first, String last) {
        Staff result = new Staff();
        result.setStaffId(UUID.randomUUID());
        result.setFirstName(first);
        result.setLastName(last);
        result.setStaffRole(StaffRole.DOCTOR);
        result.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        return result;
    }

    private void stubEmptyAggregates() {
        when(appointments.countAppointmentsByStatus(any(), any(), any(), any())).thenReturn(List.of());
        when(appointments.averageWaitMinutes(any(), any(), any(), any())).thenReturn(0L);
        when(appointments.averageWaitByDepartment(any(), any(), any(), any())).thenReturn(List.of());
        when(appointments.arrivalsByHour(any(), any(), any(), any())).thenReturn(List.of());
        when(departments.findAll()).thenReturn(List.of());
        when(staff.findAll()).thenReturn(List.of());
    }

    @Test
    void buildsMetricsFromAggregateRowsWithoutLoadingAppointments() {
        when(appointments.countAppointmentsByStatus(any(), any(), any(), any())).thenReturn(List.of(
                new Object[]{AppointmentStatus.BOOKED.ordinal(), 6L},
                new Object[]{AppointmentStatus.COMPLETED.ordinal(), 3L},
                new Object[]{AppointmentStatus.CANCELLED.ordinal(), 2L},
                new Object[]{AppointmentStatus.NOSHOW.ordinal(), 1L}
        ));
        when(appointments.averageWaitMinutes(any(), any(), any(), any())).thenReturn(28L);
        when(appointments.averageWaitByDepartment(any(), any(), any(), any())).thenReturn(List.of());
        when(appointments.arrivalsByHour(any(), any(), any(), any())).thenReturn(List.of());
        when(departments.findAll()).thenReturn(List.of());
        when(staff.findAll()).thenReturn(List.of());

        AnalyticsResponse response = service.getAnalytics(from, to, departmentId, doctorId);

        assertEquals(12, response.metrics().appointments());
        assertEquals(3, response.metrics().completed());
        assertEquals(2, response.metrics().cancelled());
        assertEquals(1, response.metrics().noShows());
        assertEquals(28, response.metrics().averageWaitMinutes());
        assertEquals(3, response.outcomes().size());
        assertEquals("COMPLETED", response.outcomes().get(0).status());
        assertEquals(3, response.outcomes().get(0).count());
        verify(appointments, never()).findAllForAnalytics();
    }

    @Test
    void pushesEveryFilterDownToAllAggregateQueries() {
        stubEmptyAggregates();

        service.getAnalytics(from, to, departmentId, doctorId);

        verify(appointments).countAppointmentsByStatus(from, to, departmentId, doctorId);
        verify(appointments).averageWaitMinutes(from, to, departmentId, doctorId);
        verify(appointments).averageWaitByDepartment(from, to, departmentId, doctorId);
        verify(appointments).arrivalsByHour(from, to, departmentId, doctorId);
    }

    @Test
    void nullFiltersArePassedThroughToTheDatabase() {
        stubEmptyAggregates();

        service.getAnalytics(null, null, null, null);

        verify(appointments).countAppointmentsByStatus(null, null, null, null);
        verify(appointments).averageWaitByDepartment(null, null, null, null);
    }

    @Test
    void mapsWaitByDepartmentAndArrivalHours() {
        when(appointments.countAppointmentsByStatus(any(), any(), any(), any())).thenReturn(List.of());
        when(appointments.averageWaitMinutes(any(), any(), any(), any())).thenReturn(0L);
        when(appointments.averageWaitByDepartment(any(), any(), any(), any())).thenReturn(List.of(
                new Object[]{"General care", 31L},
                new Object[]{"Unassigned", 12L}
        ));
        when(appointments.arrivalsByHour(any(), any(), any(), any())).thenReturn(List.of(
                new Object[]{8, 4L},
                new Object[]{11, 9L}
        ));
        when(departments.findAll()).thenReturn(List.of());
        when(staff.findAll()).thenReturn(List.of());

        AnalyticsResponse response = service.getAnalytics(from, to, null, null);

        assertEquals(2, response.waitByDepartment().size());
        assertEquals("General care", response.waitByDepartment().get(0).department());
        assertEquals(31, response.waitByDepartment().get(0).averageWaitMinutes());
        assertEquals("Unassigned", response.waitByDepartment().get(1).department());
        assertEquals(List.of(new AnalyticsResponse.ArrivalHour(8, 4), new AnalyticsResponse.ArrivalHour(11, 9)),
                response.peakArrivalHours());
    }

    @Test
    void buildsFilterOptionsFromReferenceTablesUsingUuidIds() {
        Department general = department("General care");
        Department dental = department("Dental");
        Staff ada = doctor("Ada", "Doctor");
        Staff kabo = doctor("Kabo", "Nurse");
        stubEmptyAggregates();
        when(departments.findAll()).thenReturn(List.of(general, dental));
        when(staff.findAll()).thenReturn(List.of(ada, kabo));

        AnalyticsResponse response = service.getAnalytics(from, to, null, null);

        assertEquals(2, response.departments().size());
        // Options are sorted by label: Dental comes before General care.
        assertEquals(dental.getDepartmentId().toString(), response.departments().get(0).id());
        assertEquals("Dental", response.departments().get(0).label());
        assertEquals(general.getDepartmentId().toString(), response.departments().get(1).id());
        assertEquals("General care", response.departments().get(1).label());
        // Ids must remain UUIDs: the admin analytics filters send them back as departmentId/doctorId.
        assertDoesNotThrow(() -> UUID.fromString(response.departments().get(0).id()));
        assertEquals(2, response.doctors().size());
        assertEquals(ada.getStaffId().toString(), response.doctors().get(0).id());
        assertEquals("Ada Doctor", response.doctors().get(0).label());
        assertEquals(kabo.getStaffId().toString(), response.doctors().get(1).id());
    }

    @Test
    void emptyAggregatesProduceZeroedMetricsAndEmptyCollections() {
        stubEmptyAggregates();

        AnalyticsResponse response = service.getAnalytics(from, to, null, null);

        assertEquals(0, response.metrics().appointments());
        assertEquals(0, response.metrics().averageWaitMinutes());
        assertTrue(response.waitByDepartment().isEmpty());
        assertTrue(response.peakArrivalHours().isEmpty());
        assertTrue(response.outcomes().stream().allMatch(outcome -> outcome.count() == 0));
    }

    @Test
    void unknownStatusOrdinalDoesNotFailTheAggregation() {
        when(appointments.countAppointmentsByStatus(any(), any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{99, 5L}
        ));
        when(appointments.averageWaitMinutes(any(), any(), any(), any())).thenReturn(0L);
        when(appointments.averageWaitByDepartment(any(), any(), any(), any())).thenReturn(List.of());
        when(appointments.arrivalsByHour(any(), any(), any(), any())).thenReturn(List.of());
        when(departments.findAll()).thenReturn(List.of());
        when(staff.findAll()).thenReturn(List.of());

        assertEquals(5, service.getAnalytics(from, to, null, null).metrics().appointments());
    }

    @Test
    void numberOfRowsUsesTheDatabaseTotalNotAListSize() {
        when(appointments.countAppointmentsByStatus(any(), any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{AppointmentStatus.BOOKED.ordinal(), 1000L}
        ));
        when(appointments.averageWaitMinutes(any(), any(), any(), any())).thenReturn(45L);
        when(appointments.averageWaitByDepartment(any(), any(), any(), any())).thenReturn(List.of());
        when(appointments.arrivalsByHour(any(), any(), any(), any())).thenReturn(List.of());
        when(departments.findAll()).thenReturn(List.of());
        when(staff.findAll()).thenReturn(List.of());

        AnalyticsResponse response = service.getAnalytics(from, to, null, null);
        assertEquals(1000, response.metrics().appointments());
        assertEquals(45, response.metrics().averageWaitMinutes());
    }
}
