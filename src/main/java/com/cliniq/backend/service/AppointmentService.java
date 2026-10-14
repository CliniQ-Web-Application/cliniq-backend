package com.cliniq.backend.service;

import com.cliniq.backend.model.Appointment;
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

    public Appointment createAppointment(Appointment appointment) {
        return appointmentRepository.save(appointment);
    }

    public Appointment updateAppointment(UUID id, Appointment details) {
        Appointment appointment = getAppointmentById(id);
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

    public void deleteAppointment(UUID id) {
        if (!appointmentRepository.existsById(id)) {
            throw new RuntimeException("Appointment not found with id: " + id);
        }
        appointmentRepository.deleteById(id);
    }
}