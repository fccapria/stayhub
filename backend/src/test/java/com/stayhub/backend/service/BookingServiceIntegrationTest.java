package com.stayhub.backend.service;

import com.stayhub.backend.config.TestSecurityConfig;
import com.stayhub.backend.dto.BookingRequestDTO;
import com.stayhub.backend.dto.BookingResponseDTO;
import com.stayhub.backend.dto.PaymentDetailsDTO;
import com.stayhub.backend.entity.BookingStatus;
import com.stayhub.backend.entity.PaymentMethod;
import com.stayhub.backend.entity.PaymentStatus;
import com.stayhub.backend.entity.Role;
import com.stayhub.backend.entity.Room;
import com.stayhub.backend.entity.User;
import com.stayhub.backend.exception.PaymentFailedException;
import com.stayhub.backend.exception.RoomNotAvailableException;
import com.stayhub.backend.repository.BookingRepository;
import com.stayhub.backend.repository.PaymentRepository;
import com.stayhub.backend.repository.RoomRepository;
import com.stayhub.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class BookingServiceIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private User testUser;
    private Room testRoom;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        testUser = User.builder()
                .id("keycloak-uuid-123")
                .email("guest@stayhub.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.CUSTOMER)
                .build();
        userRepository.save(testUser);

        testRoom = Room.builder()
                .name("Deluxe Suite")
                .description("A beautiful room with sea view")
                .capacity(2)
                .pricePerNight(new BigDecimal("120.00"))
                .owner(testUser)
                .build();
        roomRepository.save(testRoom);
    }

    @Test
    void testCreateBooking_SuccessWithCardPayment() {
        PaymentDetailsDTO paymentDetails = PaymentDetailsDTO.builder()
                .cardNumber("4000123456780000") // Wiremock returns success (ends in 0000)
                .cardHolder("John Doe")
                .cvv("123")
                .expirationDate("12/28")
                .build();

        BookingRequestDTO request = BookingRequestDTO.builder()
                .roomId(testRoom.getId())
                .checkIn(LocalDate.now().plusDays(2))
                .checkOut(LocalDate.now().plusDays(5))
                .paymentMethod(PaymentMethod.CLASSIC_CARD)
                .paymentDetails(paymentDetails)
                .build();

        BookingResponseDTO response = bookingService.createBooking(testUser.getId(), request);

        assertNotNull(response);
        assertEquals(BookingStatus.CONFIRMED, response.getStatus());
        assertEquals(PaymentStatus.COMPLETED, response.getPaymentStatus());
        assertEquals(new BigDecimal("360.00"), response.getTotalPrice());
        assertEquals("TX-CARD-0000-OK", response.getTransactionReference());
    }

    @Test
    void testCreateBooking_DeclinedCardPayment() {
        PaymentDetailsDTO paymentDetails = PaymentDetailsDTO.builder()
                .cardNumber("4000123456784444") // Wiremock returns decline (ends in 4444)
                .cardHolder("John Doe")
                .cvv("123")
                .expirationDate("12/28")
                .build();

        BookingRequestDTO request = BookingRequestDTO.builder()
                .roomId(testRoom.getId())
                .checkIn(LocalDate.now().plusDays(2))
                .checkOut(LocalDate.now().plusDays(5))
                .paymentMethod(PaymentMethod.CLASSIC_CARD)
                .paymentDetails(paymentDetails)
                .build();

        assertThrows(PaymentFailedException.class, () -> 
            bookingService.createBooking(testUser.getId(), request)
        );
    }

    @Test
    void testCreateBooking_OverlappingDates_ThrowsException() {
        PaymentDetailsDTO paymentDetails = PaymentDetailsDTO.builder()
                .cardNumber("4000123456780000")
                .cardHolder("John Doe")
                .cvv("123")
                .expirationDate("12/28")
                .build();

        BookingRequestDTO request1 = BookingRequestDTO.builder()
                .roomId(testRoom.getId())
                .checkIn(LocalDate.now().plusDays(2))
                .checkOut(LocalDate.now().plusDays(5))
                .paymentMethod(PaymentMethod.CLASSIC_CARD)
                .paymentDetails(paymentDetails)
                .build();

        bookingService.createBooking(testUser.getId(), request1);

        BookingRequestDTO request2 = BookingRequestDTO.builder()
                .roomId(testRoom.getId())
                .checkIn(LocalDate.now().plusDays(3))
                .checkOut(LocalDate.now().plusDays(6))
                .paymentMethod(PaymentMethod.CLASSIC_CARD)
                .paymentDetails(paymentDetails)
                .build();

        assertThrows(RoomNotAvailableException.class, () ->
            bookingService.createBooking(testUser.getId(), request2)
        );
    }
}
