package com.cliniq.backend.booking;

import com.cliniq.backend.config.SecurityConfig;
import com.cliniq.backend.controller.PatientController;
import com.cliniq.backend.enums.StaffRole;
import com.cliniq.backend.model.Patient;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.StaffRepository;
import com.cliniq.backend.service.AccessPolicy;
import com.cliniq.backend.service.PatientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PatientProfileUpdateControllerTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String BODY = """
            {"patientName":" Ada ","patientSurname":" Patient ","nationalID":" 12345 ","phone":" 123456 "}
            """;
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private PatientService service;
    private StaffRepository staff;
    private JwtDecoder decoder;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        service = context.getBean(PatientService.class);
        staff = context.getBean(StaffRepository.class);
        decoder = context.getBean(JwtDecoder.class);
        // Updates preserve the stored email and do not require an email claim.
        jwt = Jwt.withTokenValue("test").header("alg", "test").subject(USER_ID.toString()).build();
        when(decoder.decode("test")).thenReturn(jwt);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", FilterChainProxy.class))
                .build();
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    @Test
    void updatesViaPutUsingAuthenticatedSubjectAndTrimmedEditableFields() throws Exception {
        Patient saved = new Patient(UUID.randomUUID(), USER_ID, "Ada", "Patient", "12345", "stored@example.com", "123456");
        when(service.updateOwnProfile(eq(USER_ID), any(Patient.class), any(Jwt.class))).thenAnswer(call -> {
            assertEquals(USER_ID.toString(), ((Jwt) call.getArgument(2)).getSubject());
            Patient details = call.getArgument(1);
            assertEquals("Ada", details.getPatientName());
            assertEquals("Patient", details.getPatientSurname());
            assertEquals("12345", details.getNationalID());
            assertEquals("123456", details.getPhone());
            assertNull(details.getPatientID());
            assertNull(details.getAuthUserId());
            assertNull(details.getPatientEmail());
            return saved;
        });
        mvc.perform(put("/api/patients/me").header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(handler().methodName("updateOwnProfile"))
                .andExpect(jsonPath("$.patientID").value(saved.getPatientID().toString()))
                .andExpect(jsonPath("$.patientEmail").value("stored@example.com"));
        verify(staff).findByAuthUserId(USER_ID);
        verify(service).updateOwnProfile(eq(USER_ID), any(Patient.class), any(Jwt.class));
        verifyNoMoreInteractions(service);
    }

    @Test
    void missingProfileReturns404WithoutFallingBackToCreation() throws Exception {
        when(service.updateOwnProfile(eq(USER_ID), any(), any(Jwt.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient profile not found"));
        mvc.perform(put("/api/patients/me").header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Patient profile not found"));
        verify(service).updateOwnProfile(eq(USER_ID), any(), any(Jwt.class));
        verifyNoMoreInteractions(service);
    }

    @Test
    void rejectsMissingBlankAndOversizedEditableFieldsBeforeServiceAccess() throws Exception {
        var mapper = JsonMapper.builder().build();
        for (String field : new String[]{"patientName", "patientSurname", "nationalID", "phone"}) {
            for (String invalid : new String[]{"", " \t ", "x".repeat(256)}) {
                var values = new java.util.HashMap<>(Map.of("patientName", "Ada", "patientSurname", "Patient",
                        "nationalID", "12345", "phone", "123456"));
                values.put(field, invalid);
                assertBadRequest(mapper.writeValueAsString(values));
            }
            var values = new java.util.HashMap<>(Map.of("patientName", "Ada", "patientSurname", "Patient",
                    "nationalID", "12345", "phone", "123456"));
            values.remove(field);
            assertBadRequest(mapper.writeValueAsString(values));
        }
        assertBadRequest("{}");
        assertBadRequest("null");
        assertBadRequest("");
        assertBadRequest("{malformed");
        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnonymousAndMalformedSubjects() throws Exception {
        mvc.perform(put("/api/patients/me").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        for (String subject : new String[]{"invalid", "1-1-1-1-1", " " + USER_ID}) {
            when(decoder.decode("test")).thenReturn(Jwt.withTokenValue("test").header("alg", "test").subject(subject).build());
            mvc.perform(put("/api/patients/me").header("Authorization", "Bearer test")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service, staff);
    }

    @Test
    void rejectsStaffEvenWhenInactiveBeforeServiceAccess() throws Exception {
        for (boolean active : new boolean[]{true, false}) {
            Staff profile = new Staff();
            profile.setAuthUserId(USER_ID);
            profile.setStaffRole(StaffRole.DOCTOR);
            profile.setActive(active);
            when(staff.findByAuthUserId(USER_ID)).thenReturn(Optional.of(profile));
            mvc.perform(put("/api/patients/me").header("Authorization", "Bearer test")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    private void assertBadRequest(String body) throws Exception {
        mvc.perform(put("/api/patients/me").header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, AccessPolicy.class, PatientController.class})
    static class TestConfig {
        @Bean
        JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }

        @Bean
        StaffRepository staffRepository() { return mock(StaffRepository.class); }

        @Bean
        PatientService patientService() { return mock(PatientService.class); }
    }
}
