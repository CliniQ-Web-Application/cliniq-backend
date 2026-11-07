package com.cliniq.backend.booking;

import com.cliniq.backend.config.BookingTimeConfig;
import com.cliniq.backend.enums.*;
import com.cliniq.backend.model.*;
import com.cliniq.backend.repository.*;
import com.cliniq.backend.service.AccessPolicy;
import com.cliniq.backend.service.BookingService;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingServiceTest {
    private final ScheduleRepository schedules = mock(ScheduleRepository.class);
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final PatientRepository patients = mock(PatientRepository.class);
    private final AccessPolicy policy = mock(AccessPolicy.class);
    private final UUID userId = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2030-01-01T09:00:00Z"), ZoneOffset.UTC);
    private final BookingService service = new BookingService(schedules, appointments, patients, policy, clock);
    private Patient patient;
    private Schedule slot;

    @BeforeEach
    void setup() {
        patient = new Patient();
        patient.setPatientID(UUID.randomUUID());
        patient.setAuthUserId(userId);
        when(patients.findByAuthUserId(userId)).thenReturn(Optional.of(patient));
        slot = validSlot();
        when(schedules.findByIdForUpdate(slot.getSlotId())).thenReturn(Optional.of(slot));
    }

    private Schedule validSlot() {
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
        result.setEndTime(LocalTime.of(10, 30));
        result.setSlotStatus(SlotStatus.OPEN);
        result.setStaff(doctor);
        return result;
    }

    @Test
    void reservesUsingAuthenticatedPatientAndLockedSlot() {
        UUID appointmentId = UUID.randomUUID();
        when(appointments.saveAndFlush(any())).thenAnswer(call -> {
            Appointment appointment = call.getArgument(0);
            assertSame(patient, appointment.getPatient());
            assertSame(slot, appointment.getSlot());
            assertSame(slot.getStaff(), appointment.getStaff());
            assertEquals(AppointmentStatus.BOOKED, appointment.getAppointmentStatus());
            assertEquals(Priority.NORMAL, appointment.getPriority());
            assertNull(appointment.getCheckInTime());
            assertEquals(LocalDateTime.of(2030, 1, 1, 9, 0), appointment.getBookedAt());
            appointment.setAppointmentId(appointmentId);
            return appointment;
        });
        var result = service.book(userId, slot.getSlotId());
        assertEquals(appointmentId, result.appointmentId());
        assertEquals("Ada Doctor", result.doctorName());
        assertEquals("General care", result.departmentName());
        assertEquals(LocalTime.of(10, 0), result.startTime());
        assertEquals(SlotStatus.BOOKED, slot.getSlotStatus());
        var order = inOrder(policy, patients, schedules, appointments);
        order.verify(policy).requirePatient(userId);
        order.verify(patients).findByAuthUserId(userId);
        order.verify(schedules).findByIdForUpdate(slot.getSlotId());
        order.verify(appointments).countBySlot_SlotId(slot.getSlotId());
        order.verify(appointments).saveAndFlush(any());
        order.verify(schedules).saveAndFlush(slot);
        verify(schedules, never()).findById(any());
    }

    @Test
    void missingProfileRequiresCompletionBeforeLocking() {
        when(patients.findByAuthUserId(userId)).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class, () -> service.book(userId, slot.getSlotId()));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertTrue(error.getReason().contains("Complete your patient profile"));
        verifyNoInteractions(schedules, appointments);
    }

    @Test
    void allOperationsRequirePatientRole() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(policy).requirePatient(userId);
        assertThrows(ResponseStatusException.class, () -> service.availability(userId));
        assertThrows(ResponseStatusException.class, () -> service.book(userId, slot.getSlotId()));
        assertThrows(ResponseStatusException.class, () -> service.list(userId));
        assertThrows(ResponseStatusException.class, () -> service.get(userId, UUID.randomUUID()));
        verifyNoInteractions(patients, schedules, appointments);
    }

    @Test
    void rejectsMissingAndUnknownSlots() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.book(userId, null)).getStatusCode());
        UUID unknown = UUID.randomUUID();
        when(schedules.findByIdForUpdate(unknown)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.book(userId, unknown)).getStatusCode());
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidAvailabilityAndBookingCandidates() {
        List<Schedule> invalid = new ArrayList<>();
        Schedule past = validSlot(); past.setSlotDate(LocalDate.of(2029, 12, 31)); invalid.add(past);
        Schedule started = validSlot(); started.setStartTime(LocalTime.of(9, 0)); invalid.add(started);
        Schedule backwards = validSlot(); backwards.setEndTime(LocalTime.of(9, 30)); invalid.add(backwards);
        Schedule zero = validSlot(); zero.setEndTime(zero.getStartTime()); invalid.add(zero);
        Schedule noDate = validSlot(); noDate.setSlotDate(null); invalid.add(noDate);
        Schedule noStart = validSlot(); noStart.setStartTime(null); invalid.add(noStart);
        Schedule noEnd = validSlot(); noEnd.setEndTime(null); invalid.add(noEnd);
        Schedule inactive = validSlot(); inactive.getStaff().setActive(false); invalid.add(inactive);
        Schedule nullActive = validSlot(); nullActive.getStaff().setActive(null); invalid.add(nullActive);
        Schedule nurse = validSlot(); nurse.getStaff().setStaffRole(StaffRole.NURSE); invalid.add(nurse);
        Schedule noDepartment = validSlot(); noDepartment.getStaff().setDepartment(null); invalid.add(noDepartment);
        Schedule noStaff = validSlot(); noStaff.setStaff(null); invalid.add(noStaff);
        Schedule booked = validSlot(); booked.setSlotStatus(SlotStatus.BOOKED); invalid.add(booked);
        Schedule blocked = validSlot(); blocked.setSlotStatus(SlotStatus.BLOCKED); invalid.add(blocked);
        Schedule noStatus = validSlot(); noStatus.setSlotStatus(null); invalid.add(noStatus);
        List<Schedule> candidates = new ArrayList<>(invalid); candidates.add(slot);
        when(schedules.findBookingCandidates(SlotStatus.OPEN, LocalDate.of(2030, 1, 1))).thenReturn(candidates);
        var result = service.availability(userId);
        assertEquals(1, result.size());
        assertEquals(slot.getSlotId(), result.getFirst().slotId());
        for (Schedule bad : invalid) {
            when(schedules.findByIdForUpdate(bad.getSlotId())).thenReturn(Optional.of(bad));
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                    () -> service.book(userId, bad.getSlotId())).getStatusCode());
        }
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAlreadyLinkedOpenSlot() {
        when(appointments.countBySlot_SlotId(slot.getSlotId())).thenReturn(1L);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.book(userId, slot.getSlotId())).getStatusCode());
        verify(appointments, never()).saveAndFlush(any());
        verify(schedules, never()).saveAndFlush(any());
    }

    @Test
    void failedInsertDoesNotMarkSlotBooked() {
        when(appointments.saveAndFlush(any())).thenThrow(new IllegalStateException("insert failed"));
        assertThrows(IllegalStateException.class, () -> service.book(userId, slot.getSlotId()));
        assertEquals(SlotStatus.OPEN, slot.getSlotStatus());
        verify(schedules, never()).saveAndFlush(any());
    }

    @Test
    void failedSlotWritePropagatesForTransactionRollback() {
        when(schedules.saveAndFlush(any())).thenThrow(new IllegalStateException("update failed"));
        assertThrows(IllegalStateException.class, () -> service.book(userId, slot.getSlotId()));
        verify(appointments).saveAndFlush(any());
    }

    @Test
    void listAndDetailAreScopedToResolvedPatient() {
        when(appointments.findBookingsByPatientId(patient.getPatientID())).thenReturn(List.of());
        assertTrue(service.list(userId).isEmpty());
        UUID otherBooking = UUID.randomUUID();
        when(appointments.findBookingByIdAndPatientId(otherBooking, patient.getPatientID())).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.get(userId, otherBooking)).getStatusCode());
        verify(appointments).findBookingsByPatientId(patient.getPatientID());
        verify(appointments).findBookingByIdAndPatientId(otherBooking, patient.getPatientID());
        verify(appointments, never()).findById(any());
    }

    @Test
    void usesClinicZoneForFutureEligibilityAndBookedTimestamp() {
        Clock zoned = Clock.fixed(clock.instant(), ZoneId.of("Asia/Tokyo"));
        BookingService clinic = new BookingService(schedules, appointments, patients, policy, zoned);
        assertThrows(ResponseStatusException.class, () -> clinic.book(userId, slot.getSlotId()));
        slot.setStartTime(LocalTime.of(19, 0)); slot.setEndTime(LocalTime.of(19, 30));
        var result = clinic.book(userId, slot.getSlotId());
        assertEquals(LocalDateTime.of(2030, 1, 1, 18, 0), result.bookedAt());
    }

    @Test
    void rejectsDaylightSavingGapsAndAmbiguities() {
        BookingService clinic = new BookingService(schedules, appointments, patients, policy,
                Clock.fixed(Instant.parse("2029-01-01T00:00:00Z"), ZoneId.of("America/New_York")));
        slot.setSlotDate(LocalDate.of(2030, 3, 10));
        slot.setStartTime(LocalTime.of(2, 0)); slot.setEndTime(LocalTime.of(3, 0));
        assertThrows(ResponseStatusException.class, () -> clinic.book(userId, slot.getSlotId()));
        slot.setSlotDate(LocalDate.of(2030, 11, 3));
        slot.setStartTime(LocalTime.of(1, 0)); slot.setEndTime(LocalTime.of(1, 30));
        assertThrows(ResponseStatusException.class, () -> clinic.book(userId, slot.getSlotId()));
    }

    @Test
    void declaresWriteTransactionAndPessimisticRowLock() throws Exception {
        assertFalse(BookingService.class.getMethod("book", UUID.class, UUID.class)
                .getAnnotation(Transactional.class).readOnly());
        assertTrue(BookingService.class.getAnnotation(Transactional.class).readOnly());
        assertEquals(LockModeType.PESSIMISTIC_WRITE, ScheduleRepository.class
                .getMethod("findByIdForUpdate", UUID.class).getAnnotation(Lock.class).value());
        var config = new BookingTimeConfig();
        assertEquals(ZoneId.of("UTC"), config.bookingClock("UTC").getZone());
        assertThrows(DateTimeException.class, () -> config.bookingClock("not-a-zone"));
    }
}
