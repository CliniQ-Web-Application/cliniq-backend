package com.cliniq.backend.dto;

import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.enums.Priority;
import com.cliniq.backend.enums.StaffRole;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Department;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.Collectors;

public record AdminAppointment(UUID appointmentId, AppointmentStatus appointmentStatus, Priority priority,
        LocalDateTime bookedAt, LocalDateTime createdAt, LocalTime appointmentTime, LocalDateTime checkInTime,
        LocalDateTime calledTime, LocalDateTime completedTime, UUID slotId, LocalDate slotDate,
        LocalTime startTime, LocalTime endTime, UUID doctorId, String doctorName, UUID departmentId,
        String departmentName, UUID patientId, String patientName, String patientEmail, String patientPhone) {
    public static AdminAppointment from(Appointment appointment) {
        var staff = appointment.getStaff();
        return from(appointment, staff == null ? null : staff.getDepartment());
    }

    public static AdminAppointment from(Appointment appointment, Department department) {
        var slot = appointment.getSlot();
        var staff = appointment.getStaff();
        var doctor = staff != null && staff.getStaffRole() == StaffRole.DOCTOR ? staff : null;

        var patient = appointment.getPatient();
        return new AdminAppointment(appointment.getAppointmentId(), appointment.getAppointmentStatus(), appointment.getPriority(),
                appointment.getBookedAt(), appointment.getCreatedAt(), appointment.getAppointmentTime(), appointment.getCheckInTime(),
                appointment.getCalledTime(), appointment.getCompletedTime(), slot == null ? null : slot.getSlotId(),
                slot == null ? null : slot.getSlotDate(), slot == null ? null : slot.getStartTime(), slot == null ? null : slot.getEndTime(),
                doctor == null ? null : doctor.getStaffId(), doctor == null ? null : fullName(doctor.getFirstName(), doctor.getLastName()),
                department == null ? null : department.getDepartmentId(), department == null ? null : department.getName(),
                patient == null ? null : patient.getPatientID(), patient == null ? null : fullName(patient.getPatientName(), patient.getPatientSurname()),
                patient == null ? null : patient.getPatientEmail(), patient == null ? null : patient.getPhone());
    }

    private static String fullName(String first, String last) {
        return Stream.of(first, last).filter(value -> value != null && !value.isBlank())
                .map(String::strip).collect(Collectors.joining(" "));
    }
}
