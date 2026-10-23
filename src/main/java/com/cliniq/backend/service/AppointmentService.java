package com.cliniq.backend.service;

import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.enums.AppointmentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.cliniq.backend.repository.AppointmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;

    public AppointmentService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    public Appointment getAppointmentById(UUID id) {
        return appointmentRepository.findById(id).orElseThrow(() -> new RuntimeException("Appointment not found with id: " + id));
    }

    @Transactional
    public Appointment createAppointment(Appointment appointment) {
        if (appointment.getAppointmentId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment ID must not be supplied");
        }
        rejectBookingMutation(appointment);
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment updateAppointment(UUID id, Appointment details) {
        Appointment appointment = getAppointmentById(id);
        rejectBookingMutation(appointment);
        rejectBookingMutation(details);
        appointment.setAppointmentStatus(details.getAppointmentStatus());
        appointment.setQrCode(details.getQrCode());
        appointment.setCheckInTime(details.getCheckInTime());
        appointment.setPriority(details.getPriority());
        appointment.setBookedAt(details.getBookedAt());
        appointment.setCalledTime(details.getCalledTime());
        appointment.setCompletedTime(details.getCompletedTime());
        appointment.setCalledBy(details.getCalledBy());
        appointment.setPatient(details.getPatient());
        appointment.setStaff(details.getStaff());
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public void deleteAppointment(UUID id) {
        Appointment appointment = getAppointmentById(id);
        if (appointment.getSlot() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot-linked bookings require a booking lifecycle operation");
        }
        appointmentRepository.delete(appointment);
    }

    private void rejectBookingMutation(Appointment appointment) {
        if (appointment.getSlot() != null || appointment.getAppointmentStatus() == AppointmentStatus.BOOKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Use the booking API for reservations");
        }
    }
}