package com.cliniq.backend.booking;

import com.cliniq.backend.config.SecurityConfig;
import com.cliniq.backend.controller.NotificationController;
import com.cliniq.backend.model.Appointment;
import com.cliniq.backend.model.Notification;
import com.cliniq.backend.model.Patient;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.NotificationRepository;
import com.cliniq.backend.repository.PatientRepository;
import com.cliniq.backend.repository.StaffRepository;
import com.cliniq.backend.service.AccessPolicy;
import com.cliniq.backend.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationControllerTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private NotificationRepository notifications;
    private StaffRepository staff;
    private PatientRepository patients;
    private JwtDecoder decoder;
    private Notification notification;
    private Staff recipientStaff;
    private Patient recipientPatient;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        notifications = context.getBean(NotificationRepository.class);
        staff = context.getBean(StaffRepository.class);
        patients = context.getBean(PatientRepository.class);
        decoder = context.getBean(JwtDecoder.class);
        when(decoder.decode("test")).thenReturn(jwt(USER_ID.toString()));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", FilterChainProxy.class)).build();
        recipientStaff = new Staff();
        recipientStaff.setAuthUserId(USER_ID);
        recipientPatient = new Patient();
        recipientPatient.setAuthUserId(USER_ID);
        notification = new Notification();
        notification.setNotificationId(UUID.randomUUID());
        notification.setMessage("Original message");
        notification.setType("NEW_BOOKING");
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.of(2030, 1, 1, 9, 0));
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(UUID.randomUUID());
        notification.setAppointment(appointment);
        when(notifications.findById(notification.getNotificationId())).thenReturn(Optional.of(notification));
        when(notifications.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    private void selectRecipient(boolean staffAccount) {
        if (staffAccount) {
            when(staff.findByAuthUserId(USER_ID)).thenReturn(Optional.of(recipientStaff));
            notification.setStaff(recipientStaff);
        } else {
            notification.setPatient(recipientPatient);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void patientAndStaffCanMarkOwnNotificationReadIdempotentlyReturningOnlyDto(boolean staffAccount) throws Exception {
        selectRecipient(staffAccount);
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(patch(readPath()).header("Authorization", "Bearer test")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"Overwrite attempt\",\"isRead\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(6))
                    .andExpect(jsonPath("$.notificationId").value(notification.getNotificationId().toString()))
                    .andExpect(jsonPath("$.message").value("Original message"))
                    .andExpect(jsonPath("$.type").value("NEW_BOOKING"))
                    .andExpect(jsonPath("$.isRead").value(true))
                    .andExpect(jsonPath("$.createdAt").value("2030-01-01T09:00:00"))
                    .andExpect(jsonPath("$.appointmentId").value(notification.getAppointment().getAppointmentId().toString()));
        }
        assertTrue(notification.getIsRead());
        verify(notifications, times(1)).save(notification);
        verifyNoInteractions(patients);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void getUsesAuthenticatedSubjectForStaffOrPatientInbox(boolean staffAccount) throws Exception {
        selectRecipient(staffAccount);
        if (staffAccount) {
            when(notifications.findByStaff_AuthUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(notification));
        } else {
            when(notifications.findByPatient_AuthUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(notification));
        }
        mvc.perform(get("/api/notifications/me").header("Authorization", "Bearer test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notificationId").value(notification.getNotificationId().toString()))
                .andExpect(jsonPath("$[0].isRead").value(false));
        verify(staff).findByAuthUserId(USER_ID);
        verify(notifications, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void otherUsersCannotMarkUnreadOrAlreadyReadNotifications(boolean staffAccount) throws Exception {
        selectRecipient(staffAccount);
        recipientPatient.setAuthUserId(UUID.randomUUID());
        recipientStaff.setAuthUserId(UUID.randomUUID());
        for (boolean read : new boolean[]{false, true}) {
            notification.setIsRead(read);
            mvc.perform(patch(readPath()).header("Authorization", "Bearer test"))
                    .andExpect(status().isForbidden());
            assertEquals(read, notification.getIsRead());
        }
        verify(notifications, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void appointmentRelationDoesNotAuthorizeRead(boolean staffAccount) throws Exception {
        selectRecipient(staffAccount);
        notification.getAppointment().setPatient(recipientPatient);
        notification.getAppointment().setStaff(recipientStaff);
        notification.setPatient(null);
        notification.setStaff(null);
        mvc.perform(patch(readPath()).header("Authorization", "Bearer test"))
                .andExpect(status().isForbidden());
        assertFalse(notification.getIsRead());
        verify(notifications, never()).save(any());
    }

    @Test
    void staffCannotMarkPatientInboxNotificationEvenWhenInactive() throws Exception {
        selectRecipient(false);
        recipientStaff.setActive(false);
        when(staff.findByAuthUserId(USER_ID)).thenReturn(Optional.of(recipientStaff));
        mvc.perform(patch(readPath()).header("Authorization", "Bearer test"))
                .andExpect(status().isForbidden());
        assertFalse(notification.getIsRead());
        verify(notifications, never()).save(any());
    }

    @Test
    void missingNotificationReturns404WithoutWrites() throws Exception {
        mvc.perform(patch("/api/notifications/" + UUID.randomUUID() + "/read")
                        .header("Authorization", "Bearer test"))
                .andExpect(status().isNotFound());
        verify(notifications, never()).save(any());
        verifyNoInteractions(staff, patients);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PATCH"})
    void anonymousRequestsAreRejectedByProductionSecurity(String method) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path(method)))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertNull(result.getHandler()));
        verifyNoInteractions(notifications, staff, patients, decoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PATCH"})
    void malformedAndMissingSubjectsReturn401BeforeRepositoryAccess(String method) throws Exception {
        for (String subject : new String[]{"invalid", "1-1-1-1-1", " " + USER_ID, "", null}) {
            when(decoder.decode("test")).thenReturn(jwt(subject));
            mvc.perform(request(HttpMethod.valueOf(method), path(method)).header("Authorization", "Bearer test"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(notifications, staff, patients);
    }

    @Test
    void nullPrincipalIsRejectedByBothControllerMethods() {
        NotificationController controller = context.getBean(NotificationController.class);
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> controller.getMyNotifications(null)).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> controller.markAsRead(notification.getNotificationId(), null)).getStatusCode());
        verifyNoInteractions(notifications, staff, patients);
    }

    private String readPath() {
        return "/api/notifications/" + notification.getNotificationId() + "/read";
    }

    private String path(String method) {
        return "GET".equals(method) ? "/api/notifications/me" : readPath();
    }

    private static Jwt jwt(String subject) {
        Jwt.Builder builder = Jwt.withTokenValue("test").header("alg", "test").claim("test", true);
        if (subject != null) builder.subject(subject);
        return builder.build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, AccessPolicy.class, NotificationController.class})
    static class TestConfig {
        @Bean
        JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }

        @Bean
        StaffRepository staffRepository() { return mock(StaffRepository.class); }

        @Bean
        PatientRepository patientRepository() { return mock(PatientRepository.class); }

        @Bean
        NotificationRepository notificationRepository() { return mock(NotificationRepository.class); }

        @Bean
        NotificationService notificationService(PatientRepository patients, StaffRepository staff,
                                              NotificationRepository notifications) {
            return new NotificationService(patients, staff, notifications, null);
        }
    }
}
