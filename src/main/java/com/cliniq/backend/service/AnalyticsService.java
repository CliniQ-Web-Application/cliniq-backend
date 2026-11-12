package com.cliniq.backend.service;

import com.cliniq.backend.dto.AnalyticsResponse;
import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.repository.AppointmentRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {
    private final AppointmentRepository appointments;

    public AnalyticsService(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    public AnalyticsResponse getAnalytics(LocalDate from, LocalDate to, UUID departmentId, UUID doctorId) {
        List<Appointment> all = appointments.findAllForAnalytics();
        List<Appointment> filtered = all.stream()
                .filter(appointment -> inDateRange(appointment, from, to))
                .filter(appointment -> departmentId == null
                        || appointment.getStaff().getDepartment() != null
                        && departmentId.equals(appointment.getStaff().getDepartment().getDepartmentId()))
                .filter(appointment -> doctorId == null || doctorId.equals(appointment.getStaff().getStaffId()))
                .toList();

        Map<AppointmentStatus, Long> counts = filtered.stream()
                .collect(Collectors.groupingBy(Appointment::getAppointmentStatus, () -> new EnumMap<>(AppointmentStatus.class), Collectors.counting()));
        List<WaitSample> waits = filtered.stream().map(this::waitSample).flatMap(java.util.Optional::stream).toList();
        long averageWait = average(waits.stream().map(WaitSample::minutes).toList());

        List<AnalyticsResponse.DepartmentWait> waitByDepartment = waits.stream()
                .collect(Collectors.groupingBy(WaitSample::department, LinkedHashMap::new, Collectors.mapping(WaitSample::minutes, Collectors.toList())))
                .entrySet().stream()
                .map(entry -> new AnalyticsResponse.DepartmentWait(entry.getKey(), average(entry.getValue())))
                .toList();

        List<AnalyticsResponse.ArrivalHour> arrivalHours = filtered.stream()
                .map(this::arrivalTime)
                .flatMap(java.util.Optional::stream)
                .collect(Collectors.groupingBy(LocalDateTime::getHour, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new AnalyticsResponse.ArrivalHour(entry.getKey(), entry.getValue()))
                .toList();

        List<AnalyticsResponse.Outcome> outcomes = List.of(
                new AnalyticsResponse.Outcome("COMPLETED", counts.getOrDefault(AppointmentStatus.COMPLETED, 0L)),
                new AnalyticsResponse.Outcome("CANCELLED", counts.getOrDefault(AppointmentStatus.CANCELLED, 0L)),
                new AnalyticsResponse.Outcome("NOSHOW", counts.getOrDefault(AppointmentStatus.NOSHOW, 0L))
        );

        return new AnalyticsResponse(
                new AnalyticsResponse.Metrics(
                        filtered.size(),
                        counts.getOrDefault(AppointmentStatus.COMPLETED, 0L),
                        counts.getOrDefault(AppointmentStatus.CANCELLED, 0L),
                        counts.getOrDefault(AppointmentStatus.NOSHOW, 0L),
                        averageWait),
                waitByDepartment,
                arrivalHours,
                outcomes,
                options(all, appointment -> appointment.getStaff().getDepartment() == null ? null : appointment.getStaff().getDepartment().getDepartmentId().toString(),
                        appointment -> appointment.getStaff().getDepartment() == null ? null : appointment.getStaff().getDepartment().getName()),
                options(all, appointment -> appointment.getStaff().getStaffId().toString(),
                        appointment -> appointment.getStaff().getFirstName() + " " + appointment.getStaff().getLastName())
        );
    }

    private boolean inDateRange(Appointment appointment, LocalDate from, LocalDate to) {
        LocalDate date = appointment.getSlot() != null && appointment.getSlot().getSlotDate() != null
                ? appointment.getSlot().getSlotDate()
                : appointment.getCreatedAt().toLocalDate();
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private java.util.Optional<WaitSample> waitSample(Appointment appointment) {
        if (appointment.getCheckInTime() == null || appointment.getCalledTime() == null) return java.util.Optional.empty();
        long minutes = Math.max(0, Duration.between(appointment.getCheckInTime(), appointment.getCalledTime()).toMinutes());
        String department = appointment.getStaff().getDepartment() == null ? "Unassigned" : appointment.getStaff().getDepartment().getName();
        return java.util.Optional.of(new WaitSample(department, minutes));
    }

    private java.util.Optional<LocalDateTime> arrivalTime(Appointment appointment) {
        return java.util.Optional.ofNullable(appointment.getCheckInTime() != null ? appointment.getCheckInTime() : appointment.getCreatedAt());
    }

    private long average(List<Long> values) {
        return values.isEmpty() ? 0 : Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private List<AnalyticsResponse.FilterOption> options(
            List<Appointment> source,
            Function<Appointment, String> id,
            Function<Appointment, String> label) {
        Map<String, String> unique = source.stream()
                .filter(appointment -> id.apply(appointment) != null && label.apply(appointment) != null)
                .map(appointment -> Map.entry(id.apply(appointment), label.apply(appointment)))
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
        return unique.entrySet().stream().map(entry -> new AnalyticsResponse.FilterOption(entry.getKey(), entry.getValue())).toList();
    }

    private record WaitSample(String department, long minutes) {}
}
