package com.cliniq.backend.service;

import com.cliniq.backend.dto.AnalyticsResponse;
import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.model.Department;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.AppointmentRepository;
import com.cliniq.backend.repository.DepartmentRepository;
import com.cliniq.backend.repository.StaffRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds analytics from database-side COUNT / AVG / GROUP BY queries. No
 * appointment history is loaded into the JVM heap, so the heap footprint is
 * independent of how many appointments the hospital has recorded.
 */
@Service
public class AnalyticsService {
    private final AppointmentRepository appointments;
    private final DepartmentRepository departments;
    private final StaffRepository staff;

    public AnalyticsService(AppointmentRepository appointments,
                            DepartmentRepository departments,
                            StaffRepository staff) {
        this.appointments = appointments;
        this.departments = departments;
        this.staff = staff;
    }

    public AnalyticsResponse getAnalytics(LocalDate from, LocalDate to, UUID departmentId, UUID doctorId) {
        Map<AppointmentStatus, Long> counts = new LinkedHashMap<>();
        for (Object[] row : appointments.countAppointmentsByStatus(from, to, departmentId, doctorId)) {
            counts.put(statusOf(row[0]), number(row[1]));
        }

        long completed = counts.getOrDefault(AppointmentStatus.COMPLETED, 0L);
        long cancelled = counts.getOrDefault(AppointmentStatus.CANCELLED, 0L);
        long noShows = counts.getOrDefault(AppointmentStatus.NOSHOW, 0L);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long averageWait = number(appointments.averageWaitMinutes(from, to, departmentId, doctorId));

        List<AnalyticsResponse.DepartmentWait> waitByDepartment = new ArrayList<>();
        for (Object[] row : appointments.averageWaitByDepartment(from, to, departmentId, doctorId)) {
            waitByDepartment.add(new AnalyticsResponse.DepartmentWait(String.valueOf(row[0]), number(row[1])));
        }

        List<AnalyticsResponse.ArrivalHour> arrivalHours = new ArrayList<>();
        for (Object[] row : appointments.arrivalsByHour(from, to, departmentId, doctorId)) {
            arrivalHours.add(new AnalyticsResponse.ArrivalHour(number(row[0]).intValue(), number(row[1])));
        }

        return new AnalyticsResponse(
                new AnalyticsResponse.Metrics(total, completed, cancelled, noShows, averageWait),
                waitByDepartment,
                arrivalHours,
                List.of(
                        new AnalyticsResponse.Outcome("COMPLETED", completed),
                        new AnalyticsResponse.Outcome("CANCELLED", cancelled),
                        new AnalyticsResponse.Outcome("NOSHOW", noShows)
                ),
                departmentOptions(),
                doctorOptions()
        );
    }

    /** Appointment statuses are persisted ordinally (the entity has no @Enumerated). */
    private AppointmentStatus statusOf(Object value) {
        int ordinal = number(value).intValue();
        AppointmentStatus[] values = AppointmentStatus.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : AppointmentStatus.BOOKED;
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private List<AnalyticsResponse.FilterOption> departmentOptions() {
        return departments.findAll().stream()
                .filter(department -> department.getDepartmentId() != null && department.getName() != null)
                .map(department -> new AnalyticsResponse.FilterOption(
                        department.getDepartmentId().toString(), department.getName()))
                .sorted(Comparator.comparing(AnalyticsResponse.FilterOption::label))
                .toList();
    }

    private List<AnalyticsResponse.FilterOption> doctorOptions() {
        return staff.findAll().stream()
                .filter(member -> member.getFirstName() != null)
                .map(member -> new AnalyticsResponse.FilterOption(
                        member.getStaffId().toString(),
                        (member.getFirstName() + " " + (member.getLastName() == null ? "" : member.getLastName())).trim()))
                .sorted(Comparator.comparing(AnalyticsResponse.FilterOption::label))
                .toList();
    }
}
