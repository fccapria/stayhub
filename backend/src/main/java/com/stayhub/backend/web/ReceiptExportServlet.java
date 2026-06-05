package com.stayhub.backend.web;

import com.stayhub.backend.entity.Booking;
import com.stayhub.backend.entity.Payment;
import com.stayhub.backend.repository.BookingRepository;
import com.stayhub.backend.repository.PaymentRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class ReceiptExportServlet extends HttpServlet {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String bookingIdStr = req.getParameter("bookingId");
        if (bookingIdStr == null || bookingIdStr.isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing bookingId parameter");
            return;
        }

        try {
            Long bookingId = Long.parseLong(bookingIdStr);
            Booking booking = bookingRepository.findByIdWithRoomAndUser(bookingId).orElse(null);

            if (booking == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Booking not found");
                return;
            }

            Payment payment = paymentRepository.findByBookingId(bookingId).orElse(null);

            resp.setContentType("text/csv; charset=utf-8");
            resp.setHeader("Content-Disposition", "attachment; filename=receipt-booking-" + bookingId + ".csv");

            try (PrintWriter writer = new PrintWriter(resp.getOutputStream(), true, StandardCharsets.UTF_8)) {
                // Write CSV UTF-8 BOM
                resp.getOutputStream().write(0xEF);
                resp.getOutputStream().write(0xBB);
                resp.getOutputStream().write(0xBF);

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
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid bookingId format");
        }
    }
}
