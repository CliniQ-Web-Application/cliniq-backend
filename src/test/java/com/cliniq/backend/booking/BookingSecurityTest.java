package com.cliniq.backend.booking;

import com.cliniq.backend.config.SecurityConfig;
import com.cliniq.backend.enums.StaffRole;
import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.StaffRepository;
import com.cliniq.backend.service.AccessPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.web.bind.annotation.RequestMethod.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookingSecurityTest {
    private static final UUID USER_ID = UUID.fromString("8c17564a-82e4-4914-b2cf-38dc57bc4369");
    private static final String TOKEN = "security-regression-token";
    private static final String HANDLED = "test route reached";
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private JwtDecoder decoder;
    private StaffRepository staff;
    private AccessPolicy accessPolicy;

    @BeforeAll
    void createSecurityContext() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        decoder = context.getBean(JwtDecoder.class);
        staff = context.getBean(StaffRepository.class);
        accessPolicy = context.getBean(AccessPolicy.class);
        // Install the production chain explicitly without spring-security-test or Boot auto-configuration.
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", FilterChainProxy.class))
                .build();
    }

    @AfterAll
    void closeSecurityContext() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void resetIdentity() {
        reset(decoder, staff);
        when(decoder.decode(TOKEN)).thenReturn(jwt(USER_ID.toString()));
        when(staff.findByAuthUserId(USER_ID)).thenReturn(Optional.empty());
    }

    @ParameterizedTest(name = "anonymous: {0}")
    @MethodSource("allRoutes")
    void unauthenticatedRequestsReturn401(Route route) throws Exception {
        assertBlocked(route, false, HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(decoder, staff);
    }

    @ParameterizedTest(name = "patient denied: {0}")
    @MethodSource("privilegedRoutes")
    void patientsCannotAccessPrivilegedRoutes(Route route) throws Exception {
        assertBlocked(route, true, HttpStatus.FORBIDDEN);
        verify(staff).findByAuthUserId(USER_ID);
    }

    @ParameterizedTest(name = "patient allowed: {0}")
    @MethodSource("patientRoutes")
    void patientsCanReachDepartmentReadsOwnProfileAndBookings(Route route) throws Exception {
        assertAllowed(route);
        verifyNoInteractions(staff);
    }

    @ParameterizedTest(name = "{0} allowed: {1}")
    @MethodSource("nonAdminReadAndLegacyRoutes")
    void activeStaffCanUseLegacyCrudAndReadStaffAndPatients(StaffRole role, Route route) throws Exception {
        staffProfile(role, true);
        assertAllowed(route);
        verify(staff).findByAuthUserId(USER_ID);
    }

    @ParameterizedTest(name = "{0} denied mutation: {1}")
    @MethodSource("nonAdminMutationRoutes")
    void nonAdminStaffCannotMutateStaffOrDepartments(StaffRole role, Route route) throws Exception {
        staffProfile(role, true);
        assertBlocked(route, true, HttpStatus.FORBIDDEN);
        verify(staff).findByAuthUserId(USER_ID);
    }

    @ParameterizedTest(name = "admin route exists: {0}")
    @MethodSource("allRoutes")
    void activeAdminCanReachEveryFakeRouteIncludingStaffRegistration(Route route) throws Exception {
        staffProfile(StaffRole.ADMIN, true);
        assertAllowed(route);
    }

    @ParameterizedTest(name = "inactive {0} denied: {1}")
    @MethodSource("inactiveStaffRoutes")
    void inactiveStaffCannotAccessPrivilegedRoutes(StaffRole role, Route route) throws Exception {
        staffProfile(role, false);
        assertBlocked(route, true, HttpStatus.FORBIDDEN);
        verify(staff).findByAuthUserId(USER_ID);
    }

    @ParameterizedTest(name = "malformed subject [{0}]")
    @MethodSource("malformedSubjects")
    void malformedSubjectsCannotAccessPrivilegedRoutes(String subject) throws Exception {
        when(decoder.decode(TOKEN)).thenReturn(jwt(subject));
        for (Route route : privilegedRoutes().toList()) {
            assertBlocked(route, true, HttpStatus.FORBIDDEN);
        }
        verifyNoInteractions(staff);
    }

    @ParameterizedTest(name = "requirePatient rejects {0}, active={1}")
    @MethodSource("staffIdentities")
    void requirePatientRejectsStaffEvenWhenInactive(StaffRole role, boolean active) {
        staffProfile(role, active);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> accessPolicy.requirePatient(USER_ID));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(staff).findByAuthUserId(USER_ID);
    }

    @Test
    void requirePatientAllowsIdentityWithoutStaffProfile() {
        assertDoesNotThrow(() -> accessPolicy.requirePatient(USER_ID));
        verify(staff).findByAuthUserId(USER_ID);
    }

    @Test
    void requirePatientRejectsMissingIdentity() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> accessPolicy.requirePatient(null));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(staff);
    }

    private void assertAllowed(Route route) throws Exception {
        mvc.perform(authenticated(route))
                .andExpect(status().isOk())
                .andExpect(handler().handlerType(FakeRoutes.class))
                .andExpect(content().string(HANDLED));
        verify(decoder, atLeastOnce()).decode(TOKEN);
    }

    private void assertBlocked(Route route, boolean authenticated, HttpStatus expected) throws Exception {
        mvc.perform(authenticated ? authenticated(route) : request(route.method(), route.path()))
                .andExpect(status().is(expected.value()))
                .andExpect(result -> assertNull(result.getHandler(), "Security must reject before dispatch"));
        if (authenticated) {
            verify(decoder, atLeastOnce()).decode(TOKEN);
        }
    }

    private MockHttpServletRequestBuilder authenticated(Route route) {
        return request(route.method(), route.path()).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
    }

    private void staffProfile(StaffRole role, boolean active) {
        Staff profile = new Staff();
        profile.setAuthUserId(USER_ID);
        profile.setStaffRole(role);
        profile.setActive(active);
        when(staff.findByAuthUserId(USER_ID)).thenReturn(Optional.of(profile));
    }

    private static Jwt jwt(String subject) {
        Jwt.Builder builder = Jwt.withTokenValue(TOKEN).header("alg", "RS256").claim("test", true);
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }

    private static Stream<Route> legacyRoutes() {
        return Stream.of("/api/appointments", "/api/schedules").flatMap(BookingSecurityTest::crudRoutes);
    }

    private static Stream<Route> crudRoutes(String base) {
        return Stream.concat(readRoutes(base), mutationRoutes(base));
    }

    private static Stream<Route> readRoutes(String base) {
        return Stream.of(new Route(HttpMethod.GET, base), new Route(HttpMethod.GET, base + "/" + USER_ID));
    }

    private static Stream<Route> mutationRoutes(String base) {
        return Stream.of(new Route(HttpMethod.POST, base), new Route(HttpMethod.PUT, base + "/" + USER_ID),
                new Route(HttpMethod.DELETE, base + "/" + USER_ID));
    }

    private static Stream<Route> adminRoutes() {
        return Stream.concat(Stream.of("/api/staff", "/api/departments")
                .flatMap(BookingSecurityTest::mutationRoutes),
                Stream.of(new Route(HttpMethod.POST, "/api/staff/register")));
    }

    private static Stream<Route> staffRoutes() {
        return Stream.concat(legacyRoutes(), Stream.of("/api/staff", "/api/patients")
                .flatMap(BookingSecurityTest::readRoutes));
    }

    private static Stream<Route> privilegedRoutes() {
        return Stream.concat(staffRoutes(), adminRoutes());
    }

    private static Stream<Route> patientRoutes() {
        return Stream.concat(readRoutes("/api/departments"), Stream.of(
                new Route(HttpMethod.GET, "/api/patients/me"),
                new Route(HttpMethod.PUT, "/api/patients/me"),
                new Route(HttpMethod.GET, "/api/bookings"),
                new Route(HttpMethod.POST, "/api/bookings"),
                new Route(HttpMethod.GET, "/api/bookings/availability"),
                new Route(HttpMethod.GET, "/api/bookings/" + USER_ID)));
    }

    private static Stream<Route> allRoutes() {
        return Stream.concat(privilegedRoutes(), patientRoutes());
    }

    private static Stream<StaffRole> nonAdminRoles() {
        return Arrays.stream(StaffRole.values()).filter(role -> role != StaffRole.ADMIN);
    }

    private static Stream<Arguments> nonAdminReadAndLegacyRoutes() {
        return nonAdminRoles().flatMap(role -> staffRoutes().map(route -> Arguments.of(role, route)));
    }

    private static Stream<Arguments> nonAdminMutationRoutes() {
        return nonAdminRoles().flatMap(role -> adminRoutes().map(route -> Arguments.of(role, route)));
    }

    private static Stream<Arguments> inactiveStaffRoutes() {
        return Arrays.stream(StaffRole.values())
                .flatMap(role -> privilegedRoutes().map(route -> Arguments.of(role, route)));
    }

    private static Stream<Arguments> staffIdentities() {
        return Arrays.stream(StaffRole.values())
                .flatMap(role -> Stream.of(Arguments.of(role, true), Arguments.of(role, false)));
    }

    private static Stream<Arguments> malformedSubjects() {
        return Stream.of("not-a-uuid", "1-1-1-1-1", "", " " + USER_ID, null).map(Arguments::of);
    }

    private record Route(HttpMethod method, String path) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, AccessPolicy.class, FakeRoutes.class})
    static class TestConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }

        @Bean
        StaffRepository staffRepository() {
            return mock(StaffRepository.class);
        }
    }

    // These handlers prove routing/filter authorization only; no production services or DB are loaded.
    @RestController
    static class FakeRoutes {
        @RequestMapping(path = {"/api/appointments", "/api/schedules", "/api/staff", "/api/departments",
                "/api/patients", "/api/bookings"}, method = {GET, POST})
        String collection() {
            return HANDLED;
        }

        @RequestMapping(path = {"/api/appointments/{id}", "/api/schedules/{id}", "/api/staff/{id}",
                "/api/departments/{id}", "/api/patients/{id}"}, method = {GET, PUT, DELETE})
        String item() {
            return HANDLED;
        }

        @RequestMapping(path = "/api/staff/register", method = POST)
        String register() {
            return HANDLED;
        }

        @RequestMapping(path = "/api/patients/me", method = {GET, PUT})
        String ownProfile() {
            return HANDLED;
        }

        @RequestMapping(path = {"/api/bookings/availability", "/api/bookings/{id}"}, method = GET)
        String booking() {
            return HANDLED;
        }
    }
}
