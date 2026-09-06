package com.stayhub.backend.controller;

import com.stayhub.backend.dto.BookingRequestDTO;
import com.stayhub.backend.dto.BookingResponseDTO;
import com.stayhub.backend.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponseDTO> createBooking(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody BookingRequestDTO request) {
        String userId = jwt.getSubject();
        BookingResponseDTO created = bookingService.createBooking(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/my")
    public ResponseEntity<List<BookingResponseDTO>> getMyBookings(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        return ResponseEntity.ok(bookingService.getBookingsByUserId(userId));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BookingResponseDTO>> getAllBookings() {
        return ResponseEntity.ok(bookingService.getAllBookings());
    }

    @GetMapping("/owner")
    @PreAuthorize("hasAnyRole('HOST', 'ADMIN')")
    public ResponseEntity<List<BookingResponseDTO>> getOwnerBookings(@AuthenticationPrincipal Jwt jwt) {
        String ownerId = jwt.getSubject();
        return ResponseEntity.ok(bookingService.getBookingsByOwnerId(ownerId));
    }

    @PutMapping("/{id}/cancel")
    @SuppressWarnings("unchecked")
    public ResponseEntity<BookingResponseDTO> cancelBooking(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        boolean isAdmin = false;
        java.util.Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && !realmAccess.isEmpty()) {
            java.util.Collection<String> roles = (java.util.Collection<String>) realmAccess.get("roles");
            if (roles != null && roles.contains("ADMIN")) {
                isAdmin = true;
            }
        }

        if (!isAdmin) {
            List<BookingResponseDTO> myBookings = bookingService.getBookingsByUserId(jwt.getSubject());
            boolean ownsBooking = myBookings.stream().anyMatch(b -> b.getId().equals(id));
            if (!ownsBooking) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        BookingResponseDTO cancelled = bookingService.cancelBooking(id);
        return ResponseEntity.ok(cancelled);
    }
}
