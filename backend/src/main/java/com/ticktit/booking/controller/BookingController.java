package com.ticktit.booking.controller;

import com.ticktit.booking.dto.BookingDtos.AssignRequest;
import com.ticktit.booking.dto.BookingDtos.BookingView;
import com.ticktit.booking.dto.BookingDtos.LockRequest;
import com.ticktit.booking.dto.BookingDtos.UnlockRequest;
import com.ticktit.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Money-changing calls need an Idempotency-Key header (ADR-010): one per user action, reused on retries. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BookingController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final BookingService bookingService;

    @PostMapping("/bookings/lock")
    public BookingView lock(@AuthenticationPrincipal Jwt jwt, @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key,
                            @Valid @RequestBody LockRequest request) {
        return bookingService.lock(caller(jwt), key, request);
    }

    @PostMapping("/bookings/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@AuthenticationPrincipal Jwt jwt, @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key,
                       @Valid @RequestBody UnlockRequest request) {
        bookingService.unlock(caller(jwt), key, request);
    }

    @PostMapping("/bookings/{id}/assign")
    public BookingView assign(@AuthenticationPrincipal Jwt jwt, @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key,
                              @PathVariable UUID id, @Valid @RequestBody AssignRequest request) {
        return bookingService.assign(caller(jwt), key, id, request);
    }

    /** The group's current booking attempt: 200 with it, or 204 when there's none. */
    @GetMapping("/groups/{groupId}/booking")
    public ResponseEntity<BookingView> current(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        return bookingService.current(caller(jwt), groupId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    private static UUID caller(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
