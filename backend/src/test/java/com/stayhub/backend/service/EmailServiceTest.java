package com.stayhub.backend.service;

import com.stayhub.backend.entity.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private PdfReceiptService pdfReceiptService;

    private EmailService emailService;

    private Booking testBooking;
    private Payment testPayment;
    private User testOwner;
    private User testGuest;
    private Room testRoom;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, pdfReceiptService);
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "fromAddress", "StayHub <noreply@stayhub.com>");
        ReflectionTestUtils.setField(emailService, "mailPassword", "dummy-app-password");

        testOwner = User.builder()
                .id("owner-1")
                .email("mario.rossi@hoststayhub.com")
                .firstName("Mario")
                .lastName("Rossi")
                .role(Role.HOST)
                .build();

        testGuest = User.builder()
                .id("guest-1")
                .email("giulia.bianchi@customerstayhub.com")
                .firstName("Giulia")
                .lastName("Bianchi")
                .role(Role.CUSTOMER)
                .build();

        testRoom = Room.builder()
                .id(10L)
                .name("Attico Vista Duomo")
                .capacity(4)
                .pricePerNight(new BigDecimal("150.00"))
                .owner(testOwner)
                .build();

        testBooking = Booking.builder()
                .id(101L)
                .user(testGuest)
                .room(testRoom)
                .checkIn(LocalDate.of(2026, 10, 1))
                .checkOut(LocalDate.of(2026, 10, 5))
                .totalPrice(new BigDecimal("600.00"))
                .status(BookingStatus.CONFIRMED)
                .build();

        testPayment = Payment.builder()
                .id(201L)
                .booking(testBooking)
                .paymentMethod(PaymentMethod.CLASSIC_CARD)
                .transactionReference("TX-TEST-12345")
                .amount(new BigDecimal("600.00"))
                .status(PaymentStatus.COMPLETED)
                .build();
    }

    @Test
    void testSendBookingConfirmation_SendsEmailToCustomer() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendBookingConfirmation(testBooking, testPayment);

        verify(pdfReceiptService, times(1)).generateReceiptPdf(eq(testBooking), eq(testPayment), any(OutputStream.class));
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void testSendHostBookingNotification_SendsEmailToHost() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendHostBookingNotification(testBooking, testPayment);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void testSendHostBookingNotification_SkipsTestDomains() {
        testOwner.setEmail("host@test.stayhub.com");

        emailService.sendHostBookingNotification(testBooking, testPayment);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void testSendHostBookingNotification_SkipsWhenEmailMissing() {
        testOwner.setEmail(null);

        emailService.sendHostBookingNotification(testBooking, testPayment);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void testSendHostBookingNotification_SkipsWhenMailDisabled() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", false);

        emailService.sendHostBookingNotification(testBooking, testPayment);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
