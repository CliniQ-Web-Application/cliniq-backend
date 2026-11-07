package com.cliniq.backend.controller;

import com.cliniq.backend.dto.BookingAvailability;
import com.cliniq.backend.dto.BookingRequest;
import com.cliniq.backend.dto.BookingResponse;
import com.cliniq.backend.service.BookingService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @GetMapping("/availability")
    public List<BookingAvailability> availability(@AuthenticationPrincipal Jwt jwt) {
        return bookings.availability(subject(jwt));
    }

    @PostMapping
    public ResponseEntity<BookingResponse> book(@AuthenticationPrincipal Jwt jwt, @RequestBody BookingRequest request) {
        UUID userId = subject(jwt);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slotId is required");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(bookings.book(userId, request.getSlotId()));
    }

    @GetMapping
    public List<BookingResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return bookings.list(subject(jwt));
    }

    @GetMapping("/{id}")
    public BookingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return bookings.get(subject(jwt), id);
    }

    private UUID subject(Jwt jwt) {
        String sub = jwt == null ? null : jwt.getSubject();
        if (sub == null || !sub.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid authenticated user is required");
        }
        return UUID.fromString(sub);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> statusError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(Map.of("message", error.getReason() == null ? "Request failed" : error.getReason()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> invalidRequest() {
        return ResponseEntity.badRequest().body(Map.of("message", "Provide only a valid slotId UUID"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> reservationConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Slot could not be reserved"));
    }
}
