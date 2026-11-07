package com.cliniq.backend.booking;

import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.enums.SlotStatus;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Schedule;
import com.cliniq.backend.repository.AppointmentRepository;
import com.cliniq.backend.repository.ScheduleRepository;
import com.cliniq.backend.repository.StaffRepository;
import com.cliniq.backend.service.AppointmentService;
import com.cliniq.backend.service.ScheduleService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BookingLegacyGuardTest {
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final ScheduleRepository schedules = mock(ScheduleRepository.class);
    private final AppointmentService appointmentService = new AppointmentService(appointments);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final ScheduleService scheduleService = new ScheduleService(schedules, appointments, staff);

    @Test
    void legacyCreationCannotMergeExistingAppointmentOrManufactureBooking() {
        Appointment input = new Appointment();
        input.setAppointmentId(UUID.randomUUID());
        assertStatus(HttpStatus.BAD_REQUEST, () -> appointmentService.createAppointment(input));
        input.setAppointmentId(null);
        input.setAppointmentStatus(AppointmentStatus.BOOKED);
        assertStatus(HttpStatus.CONFLICT, () -> appointmentService.createAppointment(input));
        input.setAppointmentStatus(AppointmentStatus.WAITING);
        input.setSlot(new Schedule());
        assertStatus(HttpStatus.CONFLICT, () -> appointmentService.createAppointment(input));
        verifyNoInteractions(appointments);
    }

    @Test
    void cannotUpdateOrDeleteSlotLinkedAppointmentRegardlessOfStatus() {
        UUID id = UUID.randomUUID();
        Appointment existing = new Appointment();
        existing.setAppointmentStatus(AppointmentStatus.CANCELLED);
        existing.setSlot(new Schedule());
        when(appointments.findById(id)).thenReturn(Optional.of(existing));
        assertStatus(HttpStatus.CONFLICT, () -> appointmentService.updateAppointment(id, new Appointment()));
        assertStatus(HttpStatus.CONFLICT, () -> appointmentService.deleteAppointment(id));
        verify(appointments, never()).save(any());
        verify(appointments, never()).delete(any());
    }

    @Test
    void cannotTurnWalkInIntoBookingViaUpdate() {
        UUID id = UUID.randomUUID();
        when(appointments.findById(id)).thenReturn(Optional.of(new Appointment()));
        Appointment details = new Appointment();
        details.setAppointmentStatus(AppointmentStatus.BOOKED);
        assertStatus(HttpStatus.CONFLICT, () -> appointmentService.updateAppointment(id, details));
        details.setAppointmentStatus(AppointmentStatus.WAITING);
        details.setSlot(new Schedule());
        assertStatus(HttpStatus.CONFLICT, () -> appointmentService.updateAppointment(id, details));
        verify(appointments, never()).save(any());
    }

    @Test
    void preservesNonBookingAppointmentCrud() {
        Appointment walkIn = new Appointment();
        walkIn.setAppointmentStatus(AppointmentStatus.WAITING);
        when(appointments.save(any())).thenAnswer(call -> call.getArgument(0));
        assertSame(walkIn, appointmentService.createAppointment(walkIn));
        UUID id = UUID.randomUUID();
        when(appointments.findById(id)).thenReturn(Optional.of(walkIn));
        Appointment details = new Appointment(); details.setAppointmentStatus(AppointmentStatus.CHECKEDIN);
        assertEquals(AppointmentStatus.CHECKEDIN, appointmentService.updateAppointment(id, details).getAppointmentStatus());
        appointmentService.deleteAppointment(id);
        verify(appointments).delete(walkIn);
    }

    @Test
    void cannotSpoofScheduleIdOrBookedState() {
        Schedule input = new Schedule(); input.setSlotId(UUID.randomUUID());
        assertStatus(HttpStatus.BAD_REQUEST, () -> scheduleService.createSchedule(input));
        input.setSlotId(null); input.setSlotStatus(SlotStatus.BOOKED);
        assertStatus(HttpStatus.CONFLICT, () -> scheduleService.createSchedule(input));
        verifyNoInteractions(schedules);
    }

    @Test
    void locksAndRejectsBookedScheduleUpdateAndDelete() {
        UUID id = UUID.randomUUID(); Schedule existing = new Schedule(); existing.setSlotStatus(SlotStatus.BOOKED);
        when(schedules.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        assertStatus(HttpStatus.CONFLICT, () -> scheduleService.updateSchedule(id, new Schedule()));
        assertStatus(HttpStatus.CONFLICT, () -> scheduleService.deleteSchedule(id));
        verify(schedules, times(2)).findByIdForUpdate(id);
        verify(schedules, never()).findById(any());
        verify(schedules, never()).save(any());
        verify(schedules, never()).delete(any());
    }

    @Test
    void rejectsLinkedScheduleEvenIfStatusIsOpen() {
        UUID id = UUID.randomUUID(); Schedule existing = new Schedule(); existing.setSlotStatus(SlotStatus.OPEN);
        when(schedules.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(appointments.existsBySlot_SlotId(id)).thenReturn(true);
        assertStatus(HttpStatus.CONFLICT, () -> scheduleService.updateSchedule(id, new Schedule()));
        assertStatus(HttpStatus.CONFLICT, () -> scheduleService.deleteSchedule(id));
        verify(schedules, never()).save(any()); verify(schedules, never()).delete(any());
    }

    @Test
    void mutableScheduleStillCannotBeUpdatedToBooked() {
        UUID id = UUID.randomUUID(); Schedule existing = new Schedule(); existing.setSlotStatus(SlotStatus.OPEN);
        when(schedules.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        Schedule details = new Schedule(); details.setSlotStatus(SlotStatus.BOOKED);
        assertStatus(HttpStatus.CONFLICT, () -> scheduleService.updateSchedule(id, details));
        verify(schedules, never()).save(any());
    }

    @Test
    void allowsUnreservedScheduleMutationAndLocksBeforeCheckingAssociation() {
        UUID id = UUID.randomUUID(); Schedule existing = new Schedule(); existing.setSlotStatus(SlotStatus.OPEN);
        when(schedules.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(schedules.save(any())).thenAnswer(call -> call.getArgument(0));
        Schedule details = new Schedule(); details.setSlotStatus(SlotStatus.BLOCKED);
        assertEquals(SlotStatus.BLOCKED, scheduleService.updateSchedule(id, details).getSlotStatus());
        var order = inOrder(schedules, appointments);
        order.verify(schedules).findByIdForUpdate(id);
        order.verify(appointments).existsBySlot_SlotId(id);
        order.verify(schedules).save(existing);
        scheduleService.deleteSchedule(id);
        verify(schedules).delete(existing);
    }

    @Test
    void scheduleMutationsHaveWriteTransactions() throws Exception {
        assertFalse(ScheduleService.class.getMethod("updateSchedule", UUID.class, Schedule.class)
                .getAnnotation(Transactional.class).readOnly());
        assertFalse(ScheduleService.class.getMethod("deleteSchedule", UUID.class)
                .getAnnotation(Transactional.class).readOnly());
    }

    private void assertStatus(HttpStatus expected, Runnable operation) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, operation::run).getStatusCode());
    }
}
