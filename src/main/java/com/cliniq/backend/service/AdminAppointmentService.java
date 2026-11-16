package com.cliniq.backend.service;

import com.cliniq.backend.dto.AdminAppointment;
import com.cliniq.backend.dto.AdminAppointmentPage;
import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.repository.AppointmentRepository;
import com.cliniq.backend.repository.QueueRepository;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Department;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
@Transactional(readOnly = true)
public class AdminAppointmentService {
    private final AppointmentRepository appointments;
    private final AccessPolicy accessPolicy;
    private final OperationalService operations;
    private final QueueRepository queues;

    public AdminAppointmentService(AppointmentRepository appointments, AccessPolicy accessPolicy, OperationalService operations,
                                   QueueRepository queues) {
        this.appointments = appointments;
        this.accessPolicy = accessPolicy;
        this.operations = operations;
        this.queues = queues;
    }

    public AdminAppointmentPage list(Jwt jwt, LocalDate from, LocalDate to, AppointmentStatus status,
                                      UUID departmentId, UUID doctorId, String search, int page, int size) {
        accessPolicy.requireAdmin(jwt);
        if (page < 0 || size < 1 || size > 100 || (from != null && to != null && from.isAfter(to))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use page >= 0, size 1–100, and from <= to");
        }
        String pattern = search == null || search.isBlank() ? null : "%" + search.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = appointments.findAdminAppointments(from, to, status, departmentId, doctorId, pattern, PageRequest.of(page, size));
        return new AdminAppointmentPage(responses(result.getContent()),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    public AdminAppointment get(Jwt jwt, UUID id) {
        accessPolicy.requireAdmin(jwt);
        return appointments.findById(id).map(this::response).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
    }

    @Transactional
    public AdminAppointment cancel(Jwt jwt, UUID id) {
        accessPolicy.requireAdmin(jwt);
        return response(operations.cancelAdmin(jwt, id));
    }

    private AdminAppointment response(Appointment appointment) {
        return responses(List.of(appointment)).getFirst();
    }

    private List<AdminAppointment> responses(List<Appointment> page) {
        List<UUID> slotlessIds = page.stream().filter(appointment -> appointment.getSlot() == null)
                .map(Appointment::getAppointmentId).distinct().toList();
        Map<UUID, Department> queueDepartments = new HashMap<>();
        if (!slotlessIds.isEmpty()) {
            for (Object[] row : queues.findAdminDepartmentsByAppointmentIds(slotlessIds)) {
                queueDepartments.put((UUID) row[0], (Department) row[1]);
            }
        }
        return page.stream().map(appointment -> {
            var staff = appointment.getStaff();
            // A queue with a missing department is not the same as an appointment with no queue.
            Department department = queueDepartments.containsKey(appointment.getAppointmentId())
                    ? queueDepartments.get(appointment.getAppointmentId()) : staff == null ? null : staff.getDepartment();
            return AdminAppointment.from(appointment, department);
        }).toList();
    }
}
