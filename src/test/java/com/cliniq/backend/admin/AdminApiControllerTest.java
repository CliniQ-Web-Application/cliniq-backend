package com.cliniq.backend.admin;

import com.cliniq.backend.config.SecurityConfig;
import com.cliniq.backend.controller.*;
import com.cliniq.backend.dto.*;
import com.cliniq.backend.enums.*;
import com.cliniq.backend.model.Schedule;
import com.cliniq.backend.service.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminApiControllerTest {
    AnnotationConfigWebApplicationContext context;
    AdminFixture f;
    MockMvc mvc;
    final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext()); context.register(TestConfig.class); context.refresh();
        f = context.getBean(AdminFixture.class); f.admin();
        when(context.getBean(JwtDecoder.class).decode("test")).thenReturn(f.jwt);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", FilterChainProxy.class)).build();
    }

    @AfterEach void close() { context.close(); }
    String scheduleBody() {
        return "{\"staffId\":\"" + f.doctor.getStaffId() + "\",\"slotDate\":\"2030-01-01\",\"startTime\":\"10:00\",\"endTime\":\"11:00\",\"capacity\":3}";
    }
    String broadcastPath() { return "/api/schedules/" + f.slot.getSlotId() + "/broadcast"; }

    @Test
    void scheduleCrudReturnsExactSafeDtoAndSuccessStatuses() throws Exception {
        String json = mvc.perform(post("/api/admin/schedules").header("Authorization", "Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content(scheduleBody()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.capacity").value(3))
                .andExpect(jsonPath("$.slotStatus").value("OPEN"))
                .andExpect(jsonPath("$.staff.availabilityStatus").value("UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        var node = mapper.readTree(json);
        assertEquals(Set.of("slotId", "slotDate", "startTime", "endTime", "slotStatus", "capacity", "staff"), node.propertyNames());
        assertEquals(Set.of("staffId", "firstName", "lastName", "staffRole", "availabilityStatus", "active", "department"), node.get("staff").propertyNames());
        assertEquals(Set.of("departmentId", "name"), node.get("staff").get("department").propertyNames());
        when(f.schedules.findAllForAdmin()).thenReturn(List.of(f.slot));
        mvc.perform(get("/api/admin/schedules").header("Authorization", "Bearer test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].slotId").value(f.slot.getSlotId().toString()));
        mvc.perform(put("/api/admin/schedules/" + f.slot.getSlotId()).header("Authorization", "Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content(scheduleBody()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slotId").value(f.slot.getSlotId().toString()));
        mvc.perform(delete("/api/admin/schedules/" + f.slot.getSlotId()).header("Authorization", "Bearer test"))
                .andExpect(status().isNoContent());
    }

    @Test
    void strictWholeCapacityRejectsDecimalStringNullAndOutOfRangeOnAdminAndSelfRoutes() throws Exception {
        for (String value : List.of("1.5", "1.0", "\"3\"", "null", "0", "10001")) {
            mvc.perform(post("/api/admin/schedules").header("Authorization", "Bearer test")
                    .contentType(MediaType.APPLICATION_JSON).content(scheduleBody().replace("\"capacity\":3", "\"capacity\":" + value)))
                    .andExpect(status().isBadRequest());
        }
        f.actor(f.doctor);
        for (String value : List.of("1.5", "1.0", "0", "10001")) {
            mvc.perform(post("/api/schedules").header("Authorization", "Bearer test")
                    .contentType(MediaType.APPLICATION_JSON).content(scheduleBody().replace("\"capacity\":3", "\"capacity\":" + value)))
                    .andExpect(status().isBadRequest());
        }
        verify(f.schedules, never()).save(any());
    }

    @Test
    void broadcastRouteReturnsOnlyRecipientCountAndAcceptsAbsenceNullOrMissingMinutes() throws Exception {
        for (String body : List.of("{\"type\":\"ABSENCE\",\"message\":\"Away\"}",
                "{\"type\":\"ABSENCE\",\"message\":\"Away\",\"delayMinutes\":null}",
                "{\"type\":\"DELAY\",\"message\":\"Later\",\"delayMinutes\":30}")) {
            mvc.perform(post(broadcastPath()).header("Authorization", "Bearer test").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andExpect(content().json("{\"recipientCount\":0}"));
        }
    }

    @Test
    void broadcastAndAdminBodiesRejectMalformedOrMissingBodyAndInvalidEnum() throws Exception {
        for (String body : List.of("", "null", "{bad}", "{\"type\":\"OTHER\",\"message\":\"test\"}")) {
            mvc.perform(post(broadcastPath()).header("Authorization", "Bearer test").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        for (String value : List.of("1.5", "1.0", "\"30\"", "null", "1441")) {
            mvc.perform(post(broadcastPath()).header("Authorization", "Bearer test").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\":\"DELAY\",\"message\":\"Later\",\"delayMinutes\":" + value + "}"))
                    .andExpect(status().isBadRequest());
        }
        verify(f.notifications, never()).saveAll(any());
    }

    @Test
    void appointmentDefaultPagingInspectAndCancelUseExactFlatDtoWithoutAuthIds() throws Exception {
        var appointment = f.appointment(f.patient(), AppointmentStatus.BOOKED);
        when(f.appointments.findAdminAppointments(null, null, null, null, null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(appointment), PageRequest.of(0, 20), 1));
        String json = mvc.perform(get("/api/admin/appointments").header("Authorization", "Bearer test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].patientName").value("Grace Patient"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(Set.of("items", "page", "size", "totalElements", "totalPages"), mapper.readTree(json).propertyNames());
        Set<String> fields = Set.of("appointmentId", "appointmentStatus", "priority", "bookedAt", "createdAt", "appointmentTime",
                "checkInTime", "calledTime", "completedTime", "slotId", "slotDate", "startTime", "endTime", "doctorId", "doctorName",
                "departmentId", "departmentName", "patientId", "patientName", "patientEmail", "patientPhone");
        assertEquals(fields, mapper.readTree(json).get("items").get(0).propertyNames());
        when(f.appointments.findById(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        String path = "/api/admin/appointments/" + appointment.getAppointmentId();
        mvc.perform(get(path).header("Authorization", "Bearer test")).andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentId").value(appointment.getAppointmentId().toString()));
        appointment.setAppointmentStatus(AppointmentStatus.CANCELLED);
        when(context.getBean(OperationalService.class).cancelAdmin(any(Jwt.class), eq(appointment.getAppointmentId()))).thenReturn(appointment);
        String cancelled = mvc.perform(patch(path + "/cancel").header("Authorization", "Bearer test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.appointmentStatus").value("CANCELLED"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(fields, mapper.readTree(cancelled).propertyNames());
    }

    @Test
    void crossDepartmentSlotlessAppointmentUsesQueueDepartmentOnListInspectAndCancel() throws Exception {
        var appointment = f.appointment(f.patient(), AppointmentStatus.BOOKED);
        appointment.setSlot(null);
        var registrar = f.profile(StaffRole.NURSE, true); registrar.setDepartment(f.department);
        appointment.setStaff(registrar);
        var treatment = new com.cliniq.backend.model.Department();
        treatment.setDepartmentId(UUID.randomUUID()); treatment.setName("Treatment department");
        when(f.queues.findAdminDepartmentsByAppointmentIds(List.of(appointment.getAppointmentId())))
                .thenReturn(Collections.singletonList(new Object[]{appointment.getAppointmentId(), treatment}));
        when(f.appointments.findAdminAppointments(null, null, null, treatment.getDepartmentId(), null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(appointment), PageRequest.of(0, 20), 1));
        when(f.appointments.findById(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        String id = treatment.getDepartmentId().toString();
        mvc.perform(get("/api/admin/appointments").header("Authorization", "Bearer test").param("departmentId", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].departmentId").value(id))
                .andExpect(jsonPath("$.items[0].departmentName").value("Treatment department"))
                .andExpect(jsonPath("$.items[0].doctorId").value(org.hamcrest.Matchers.nullValue()));
        String path = "/api/admin/appointments/" + appointment.getAppointmentId();
        mvc.perform(get(path).header("Authorization", "Bearer test")).andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(id)).andExpect(jsonPath("$.departmentName").value("Treatment department"));
        when(context.getBean(OperationalService.class).cancelAdmin(any(Jwt.class), eq(appointment.getAppointmentId()))).thenAnswer(call -> {
            appointment.setAppointmentStatus(AppointmentStatus.CANCELLED); return appointment;
        });
        mvc.perform(patch(path + "/cancel").header("Authorization", "Bearer test")).andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(id)).andExpect(jsonPath("$.departmentName").value("Treatment department"))
                .andExpect(jsonPath("$.appointmentStatus").value("CANCELLED"));
    }

    @Test
    void appointmentQueryParametersBindExactlyAndBadFiltersReturn400() throws Exception {
        var from = LocalDate.of(2030, 1, 1); var to = from.plusDays(5);
        when(f.appointments.findAdminAppointments(from, to, AppointmentStatus.WAITING, f.department.getDepartmentId(), f.doctor.getStaffId(),
                "%grace%", PageRequest.of(2, 10))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 10), 0));
        mvc.perform(get("/api/admin/appointments").header("Authorization", "Bearer test")
                .param("from", from.toString()).param("to", to.toString()).param("status", "WAITING")
                .param("departmentId", f.department.getDepartmentId().toString()).param("doctorId", f.doctor.getStaffId().toString())
                .param("search", "Grace").param("page", "2").param("size", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2));
        for (String[] invalid : new String[][]{{"from", "not-a-date"}, {"status", "INVALID"}, {"doctorId", "not-uuid"}, {"size", "101"}, {"page", "-1"}}) {
            mvc.perform(get("/api/admin/appointments").header("Authorization", "Bearer test").param(invalid[0], invalid[1]))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void serviceOwnershipAndProductionSecurityProtectRealRoutes() throws Exception {
        mvc.perform(post(broadcastPath()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        f.actor(f.profile(StaffRole.DOCTOR, true));
        mvc.perform(post(broadcastPath()).header("Authorization", "Bearer test").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"ABSENCE\",\"message\":\"Away\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/schedules").header("Authorization", "Bearer test")).andExpect(status().isForbidden());
        f.actor(f.profile(StaffRole.ADMIN, false));
        mvc.perform(get("/api/admin/appointments").header("Authorization", "Bearer test")).andExpect(status().isForbidden());
        verifyNoInteractions(f.notifications);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, AdminScheduleController.class, AdminAppointmentController.class,
            ScheduleBroadcastController.class, ScheduleController.class, ScheduleAdminExceptionHandler.class})
    static class TestConfig {
        @Bean AdminFixture fixture() { return new AdminFixture(); }
        @Bean AccessPolicy accessPolicy(AdminFixture f) { return f.policy; }
        @Bean ScheduleService scheduleService(AdminFixture f) { return new ScheduleService(f.schedules, f.appointments, f.staff, f.audit, f.policy, f.clock); }
        @Bean OperationalService operations() { return mock(OperationalService.class); }
        @Bean AdminAppointmentService appointments(AdminFixture f, OperationalService operations) { return new AdminAppointmentService(f.appointments, f.policy, operations, f.queues); }
        @Bean ScheduleBroadcastService broadcasts(AdminFixture f) { return new ScheduleBroadcastService(f.schedules, f.appointments, f.staff, f.notifications, f.audit, f.clock); }
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
    }
}
