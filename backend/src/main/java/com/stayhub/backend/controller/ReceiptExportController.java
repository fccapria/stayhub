package com.stayhub.backend.controller;

import com.stayhub.backend.entity.Booking;
import com.stayhub.backend.entity.Payment;
import com.stayhub.backend.repository.BookingRepository;
import com.stayhub.backend.repository.PaymentRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import com.stayhub.backend.service.PdfReceiptService;

@RestController
@RequestMapping("/api/v1/exports")
@RequiredArgsConstructor
public class ReceiptExportController {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PdfReceiptService pdfReceiptService;

    @GetMapping("/receipt")
    @SuppressWarnings("unchecked")
    public void getReceipt(
            @RequestParam("bookingId") Long bookingId,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse resp) throws IOException {

        Booking booking = bookingRepository.findByIdWithRoomAndUser(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        String currentUserId = jwt.getSubject();
        boolean isAdmin = false;
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && !realmAccess.isEmpty()) {
            Collection<String> roles = (Collection<String>) realmAccess.get("roles");
            if (roles != null && roles.contains("ADMIN")) {
                isAdmin = true;
            }
        }

        boolean isGuest = booking.getUser() != null && currentUserId.equals(booking.getUser().getId());
        boolean isRoomOwner = booking.getRoom() != null && booking.getRoom().getOwner() != null 
                && currentUserId.equals(booking.getRoom().getOwner().getId());
        if (!isAdmin && !isGuest && !isRoomOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to booking receipt");
        }

        Payment payment = paymentRepository.findByBookingId(bookingId).orElse(null);

        resp.setContentType("application/pdf");
        resp.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=receipt-booking-" + bookingId + ".pdf");

        try {
            pdfReceiptService.generateReceiptPdf(booking, payment, resp.getOutputStream());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error generating PDF receipt: " + e.getMessage());
        }
    }
}
