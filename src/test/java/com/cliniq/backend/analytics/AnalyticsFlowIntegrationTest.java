package com.cliniq.backend.analytics;

import com.cliniq.backend.controller.AnalyticsController;
import com.cliniq.backend.dto.AnalyticsResponse;
import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.model.Department;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.AppointmentRepository;
import com.cliniq.backend.repository.DepartmentRepository;
import com.cliniq.backend.repository.StaffRepository;
import com.cliniq.backend.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Exercises the real analytics controller + service with mocked aggregate queries. */
class AnalyticsFlowIntegrationTest {
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final AnalyticsController controller = new AnalyticsController(
            new AnalyticsService(appointments, departments, staff));
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void returnsCompleteAnalyticsEnvelopeWithUuidFilterIds() {
        Department general = new Department();
        general.setDepartmentId(UUID.randomUUID());
        general.setName("General care");

        Staff doctor = new Staff();
        doctor.setStaffId(UUID.randomUUID());
        doctor.setFirstName("Ada");
        doctor.setLastName("Doctor");

        when(appointments.countAppointmentsByStatus(any(), any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{AppointmentStatus.BOOKED.ordinal(), 2L},
                new Object[]{AppointmentStatus.COMPLETED.ordinal(), 5L},
                new Object[]{AppointmentStatus.CANCELLED.ordinal(), 1L},
                new Object[]{AppointmentStatus.NOSHOW.ordinal(), 1L}
        ));
        when(appointments.averageWaitMinutes(any(), any(), any(), any())).thenReturn(22L);
        when(appointments.averageWaitByDepartment(any(), any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{"General care", 25L}
        ));
        when(appointments.arrivalsByHour(any(), any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{9, 3L}
        ));
        when(departments.findAll()).thenReturn(List.of(general));
        when(staff.findAll()).thenReturn(List.of(doctor));

        LocalDate from = LocalDate.of(2030, 1, 1);
        LocalDate to = LocalDate.of(2030, 1, 7);
        UUID departmentId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();

        AnalyticsResponse response = controller.getAnalytics(from, to, departmentId, doctorId);
        ObjectNode json = mapper.valueToTree(response);

        assertEquals(9, json.get("metrics").get("appointments").asInt());
        assertEquals(5, json.get("metrics").get("completed").asInt());
        assertEquals(22, json.get("metrics").get("averageWaitMinutes").asInt());
        assertEquals("General care", json.get("waitByDepartment").get(0).get("department").asText());
        assertEquals(9, json.get("peakArrivalHours").get(0).get("hour").asInt());
        assertEquals(general.getDepartmentId().toString(), json.get("departments").get(0).get("id").asText());
        assertEquals(doctor.getStaffId().toString(), json.get("doctors").get(0).get("id").asText());
        assertDoesNotThrow(() -> UUID.fromString(json.get("departments").get(0).get("id").asText()));

        verify(appointments).countAppointmentsByStatus(from, to, departmentId, doctorId);
        verify(appointments, never()).findAllForAnalytics();
    }

    @Test
    void emptyDatabaseProducesZeroedMetrics() {
        when(appointments.countAppointmentsByStatus(any(), any(), any(), any())).thenReturn(List.of());
        when(appointments.averageWaitMinutes(any(), any(), any(), any())).thenReturn(0L);
        when(appointments.averageWaitByDepartment(any(), any(), any(), any())).thenReturn(List.of());
        when(appointments.arrivalsByHour(any(), any(), any(), any())).thenReturn(List.of());
        when(departments.findAll()).thenReturn(List.of());
        when(staff.findAll()).thenReturn(List.of());

        ObjectNode json = mapper.valueToTree(controller.getAnalytics(null, null, null, null));

        assertEquals(0, json.get("metrics").get("appointments").asInt());
        assertEquals(0, json.get("metrics").get("averageWaitMinutes").asInt());
        assertTrue(json.get("waitByDepartment").isEmpty());
        assertTrue(json.get("peakArrivalHours").isEmpty());
        assertTrue(json.get("departments").isEmpty());
        assertTrue(json.get("doctors").isEmpty());
    }
}
