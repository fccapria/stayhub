package com.stayhub.backend.controller;

import com.stayhub.backend.entity.Booking;
import com.stayhub.backend.entity.Payment;
import com.stayhub.backend.repository.BookingRepository;
import com.stayhub.backend.repository.PaymentRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/exports")
@RequiredArgsConstructor
public class ReceiptExportController {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    @GetMapping("/receipt")
    public void getReceipt(@RequestParam("bookingId") Long bookingId, HttpServletResponse resp) throws IOException {
        Booking booking = bookingRepository.findByIdWithRoomAndUser(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        Payment payment = paymentRepository.findByBookingId(bookingId).orElse(null);

        resp.setContentType("text/csv; charset=utf-8");
        resp.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=receipt-booking-" + bookingId + ".csv");

        // Write CSV UTF-8 BOM
        resp.getOutputStream().write(0xEF);
        resp.getOutputStream().write(0xBB);
        resp.getOutputStream().write(0xBF);

        try (PrintWriter writer = new PrintWriter(resp.getOutputStream(), true, StandardCharsets.UTF_8)) {
            writer.println("StayHub Reservation Receipt");
            writer.println("Receipt Number,REC-" + bookingId + "-" + (payment != null ? payment.getId() : "PENDING"));
            writer.println("Room Name," + booking.getRoom().getName());
            writer.println("Guest Email," + booking.getUser().getEmail());
            writer.println("Check-in Date," + booking.getCheckIn());
            writer.println("Check-out Date," + booking.getCheckOut());
            writer.println("Total Paid (EUR)," + booking.getTotalPrice());
            writer.println("Booking Status," + booking.getStatus());
            writer.println("Payment Method," + (payment != null ? payment.getPaymentMethod() : "N/A"));
            writer.println("Transaction Reference," + (payment != null && payment.getTransactionReference() != null ? payment.getTransactionReference() : "N/A"));
        }
    }
}
