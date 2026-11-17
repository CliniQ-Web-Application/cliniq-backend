package com.cliniq.backend.booking;

import com.cliniq.backend.controller.BookingController;
import com.cliniq.backend.dto.*;
import com.cliniq.backend.enums.*;
import com.cliniq.backend.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingControllerTest {
    private final BookingService service = mock(BookingService.class);
    private final BookingController controller = new BookingController(service);
    private final UUID userId = UUID.randomUUID();
    private final JsonMapper mapper = JsonMapper.builder().build();

    private Jwt jwt(String subject) {
        return Jwt.withTokenValue("test").header("alg", "test").subject(subject).build();
    }

    @Test
    void derivesOwnershipFromJwtAndReturnsCreatedDto() {
        UUID slotId = UUID.randomUUID();
        BookingRequest request = new BookingRequest(); request.setSlotId(slotId);
        var booking = new BookingResponse(UUID.randomUUID(), AppointmentStatus.BOOKED, slotId,
                LocalDate.of(2030, 1, 1), LocalTime.of(10, 0), LocalTime.of(10, 30),
                "General care", "Ada Doctor", LocalDateTime.of(2030, 1, 1, 9, 0));
        when(service.book(userId, slotId)).thenReturn(booking);
        var response = controller.book(jwt(userId.toString()), request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(booking, response.getBody());
        verify(service).book(userId, slotId);
    }

    @Test
    void allEndpointsRejectInvalidSubjectBeforeServiceAccess() {
        for (String invalid : List.of("not-a-uuid", "1-1-1-1-1", "", " " + userId)) {
            Jwt jwt = jwt(invalid);
            assertUnauthorized(() -> controller.availability(jwt));
            assertUnauthorized(() -> controller.book(jwt, new BookingRequest()));
            assertUnauthorized(() -> controller.list(jwt));
            assertUnauthorized(() -> controller.get(jwt, UUID.randomUUID()));
        }
        assertUnauthorized(() -> controller.list(null));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingSubject() {
        Jwt noSubject = Jwt.withTokenValue("test").header("alg", "test").claim("other", "test").build();
        assertUnauthorized(() -> controller.availability(noSubject));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnknownOwnershipAndStatusFieldsRegardlessOfMapperDefault() {
        String slot = UUID.randomUUID().toString();
        assertEquals(UUID.fromString(slot), mapper.readValue("{\"slotId\":\"" + slot + "\"}", BookingRequest.class).getSlotId());
        for (String field : List.of("patient", "patientId", "authUserId", "appointmentStatus", "staff", "bookedAt", "extra")) {
            String json = "{\"slotId\":\"" + slot + "\",\"" + field + "\":null}";
            assertThrows(RuntimeException.class, () -> mapper.readValue(json, BookingRequest.class));
        }
        assertThrows(RuntimeException.class, () -> mapper.readValue("{\"slotId\":\"bad\"}", BookingRequest.class));
    }

    @Test
    void availabilityJsonHasOnlyAgreedDisplayFields() {
        var availability = new BookingAvailability(UUID.randomUUID(), LocalDate.of(2030, 1, 1),
                LocalTime.of(10, 0), LocalTime.of(10, 30), SlotStatus.OPEN,
                new BookingAvailability.Doctor(UUID.randomUUID(), "Ada", "Doctor", StaffRole.DOCTOR, AvailabilityStatus.AVAILABLE, true,
                        new BookingAvailability.Department(UUID.randomUUID(), "General care")),
                10, 3);
        var json = mapper.valueToTree(availability);
        assertEquals(Set.of("slotId", "slotDate", "startTime", "endTime", "slotStatus", "staff", "capacity", "bookedCount"), json.propertyNames());
        assertEquals(Set.of("staffId", "firstName", "lastName", "staffRole", "availabilityStatus", "active", "department"),
                json.get("staff").propertyNames());
        assertEquals(Set.of("departmentId", "name"), json.get("staff").get("department").propertyNames());
    }

    @Test
    void exposesOnlySafeErrorMessages() {
        var status = controller.statusError(new ResponseStatusException(HttpStatus.CONFLICT, "Complete your patient profile"));
        assertEquals(HttpStatus.CONFLICT, status.getStatusCode());
        assertEquals(Map.of("message", "Complete your patient profile"), status.getBody());
        assertEquals(HttpStatus.BAD_REQUEST, controller.invalidRequest().getStatusCode());
        assertEquals(HttpStatus.CONFLICT, controller.reservationConflict().getStatusCode());
    }

    private void assertUnauthorized(Runnable operation) {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class, operation::run).getStatusCode());
    }
}
