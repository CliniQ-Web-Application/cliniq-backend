package com.cliniq.backend.booking;

import com.cliniq.backend.config.BookingTimeConfig;
import com.cliniq.backend.enums.*;
import com.cliniq.backend.model.*;
import com.cliniq.backend.repository.*;
import com.cliniq.backend.service.AccessPolicy;
import com.cliniq.backend.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingCapacityTest {
    private final ScheduleRepository schedules = mock(ScheduleRepository.class);
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final PatientRepository patients = mock(PatientRepository.class);
    private final AccessPolicy policy = mock(AccessPolicy.class);
    private final UUID userId = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2030-01-01T09:00:00Z"), ZoneOffset.UTC);
    private final BookingService service = new BookingService(schedules, appointments, patients, policy, clock);
    private Patient patient;
    private Schedule window;

    @BeforeEach
    void setup() {
        patient = new Patient();
        patient.setPatientID(UUID.randomUUID());
        patient.setAuthUserId(userId);
        when(patients.findByAuthUserId(userId)).thenReturn(Optional.of(patient));
        window = windowSlot();
        when(schedules.findByIdForUpdate(window.getSlotId())).thenReturn(Optional.of(window));
        when(appointments.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    private Schedule windowSlot() {
        Department department = new Department();
        department.setDepartmentId(UUID.randomUUID());
        department.setName("General care");
        Staff doctor = new Staff();
        doctor.setStaffId(UUID.randomUUID());
        doctor.setFirstName("Ada");
        doctor.setLastName("Doctor");
        doctor.setStaffRole(StaffRole.DOCTOR);
        doctor.setActive(true);
        doctor.setDepartment(department);
        Schedule result = new Schedule();
        result.setSlotId(UUID.randomUUID());
        result.setSlotDate(LocalDate.of(2030, 1, 1));
        result.setStartTime(LocalTime.of(10, 0));
        result.setEndTime(LocalTime.of(15, 0));
        result.setSlotStatus(SlotStatus.OPEN);
        result.setStaff(doctor);
        result.setCapacity(2);
        return result;
    }

    @Test
    void windowAcceptsMultipleBookingsUntilCapacityThenCloses() {
        when(appointments.countOccupyingBySlotId(window.getSlotId())).thenReturn(0L, 1L, 2L);
        service.book(userId, window.getSlotId());
        assertEquals(SlotStatus.OPEN, window.getSlotStatus());
        verify(schedules, never()).saveAndFlush(any());

        service.book(userId, window.getSlotId());
        assertEquals(SlotStatus.BOOKED, window.getSlotStatus());
        verify(schedules, times(1)).saveAndFlush(window);

        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.book(userId, window.getSlotId())).getStatusCode());
        verify(appointments, times(2)).saveAndFlush(any());
    }

    @Test
    void fullWindowIsHiddenFromAvailabilityAndReportsCounts() {
        Schedule full = windowSlot();
        full.setSlotId(UUID.randomUUID());
        when(schedules.findBookingCandidates(SlotStatus.OPEN, LocalDate.of(2030, 1, 1))).thenReturn(List.of(full));
        when(appointments.countOccupyingBySlotIds(any())).thenReturn(Collections.singletonList(
                new Object[]{full.getSlotId(), 2L}));
        assertTrue(service.availability(userId).isEmpty());
    }

    @Test
    void availabilityReportsEffectiveCapacityAndBookedCount() {
        when(schedules.findBookingCandidates(SlotStatus.OPEN, LocalDate.of(2030, 1, 1))).thenReturn(List.of(window));
        when(appointments.countOccupyingBySlotIds(any())).thenReturn(Collections.singletonList(
                new Object[]{window.getSlotId(), 1L}));
        var result = service.availability(userId);
        assertEquals(1, result.size());
        assertEquals(2, result.getFirst().capacity());
        assertEquals(1L, result.getFirst().bookedCount());
    }

    @Test
    void legacySlotWithoutCapacityBooksOnce() {
        window.setCapacity(null);
        when(appointments.countOccupyingBySlotId(window.getSlotId())).thenReturn(0L);
        service.book(userId, window.getSlotId());
        assertEquals(SlotStatus.BOOKED, window.getSlotStatus());
        assertEquals(1, window.effectiveCapacity());
    }
}
