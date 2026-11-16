package com.cliniq.backend.operational;

import com.cliniq.backend.config.SecurityConfig;
import com.cliniq.backend.controller.OperationalController;
import com.cliniq.backend.enums.AppointmentStatus;
import com.cliniq.backend.service.AccessPolicy;
import com.cliniq.backend.service.OperationalService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QueuePositionControllerTest {
    private AnnotationConfigWebApplicationContext context;
    private OperationalFixture fixture;
    private MockMvc mvc;
    private UUID id;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        fixture = context.getBean(OperationalFixture.class);
        id = fixture.entry(1, AppointmentStatus.WAITING).getAppointment().getAppointmentId();
        when(context.getBean(JwtDecoder.class).decode("test")).thenReturn(fixture.jwt);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", FilterChainProxy.class)).build();
    }

    @AfterEach
    void cleanup() { context.close(); }

    @Test
    void authenticatedOwnerGetsExactDtoContractWithoutPersistence() throws Exception {
        mvc.perform(get(path()).header("Authorization", "Bearer test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$.appointmentId").value(id.toString()))
                .andExpect(jsonPath("$.queuePosition").value(1))
                .andExpect(jsonPath("$.queueStatus").value("WAITING"))
                .andExpect(jsonPath("$.estimatedWaitMinutes").value(0));
        assertEquals(99, fixture.entries.getFirst().getPosition());
        verify(fixture.queues, never()).save(any());
        verify(fixture.appointments, never()).save(any());
    }

    @Test
    void queuedTerminalResponseHasNullPositionAndZeroEstimate() throws Exception {
        fixture.entries.getFirst().getAppointment().setAppointmentStatus(AppointmentStatus.COMPLETED);
        mvc.perform(get(path()).header("Authorization", "Bearer test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$.queuePosition").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.queueStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.estimatedWaitMinutes").value(0));
    }

    @Test
    void otherUsersAreForbiddenAndMissingOrUnqueuedRecordsReturn404() throws Exception {
        fixture.patient.setAuthUserId(UUID.randomUUID());
        mvc.perform(get(path()).header("Authorization", "Bearer test")).andExpect(status().isForbidden());
        fixture.patient.setAuthUserId(fixture.userId);
        when(fixture.queues.findByAppointment(any())).thenReturn(Optional.empty());
        mvc.perform(get(path()).header("Authorization", "Bearer test")).andExpect(status().isNotFound());
        mvc.perform(get("/api/appointments/" + UUID.randomUUID() + "/queue-position")
                .header("Authorization", "Bearer test")).andExpect(status().isNotFound());
    }

    @Test
    void productionSecurityRejectsAnonymousRequestBeforeController() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        verifyNoInteractions(fixture.appointments, fixture.queues);
    }

    private String path() { return "/api/appointments/" + id + "/queue-position"; }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, OperationalController.class})
    static class TestConfig {
        @Bean OperationalFixture fixture() { return new OperationalFixture(); }
        @Bean OperationalService operationalService(OperationalFixture fixture) { return fixture.service; }
        @Bean AccessPolicy accessPolicy(OperationalFixture fixture) { return new AccessPolicy(fixture.staff); }
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
    }
}
