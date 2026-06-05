package com.stayhub.backend.service;

import com.stayhub.backend.dto.BookingRequestDTO;
import com.stayhub.backend.dto.BookingResponseDTO;
import com.stayhub.backend.entity.*;
import com.stayhub.backend.exception.PaymentFailedException;
import com.stayhub.backend.exception.ResourceNotFoundException;
import com.stayhub.backend.exception.RoomNotAvailableException;
import com.stayhub.backend.mapper.DtoMapper;
import com.stayhub.backend.repository.BookingRepository;
import com.stayhub.backend.repository.PaymentRepository;
import com.stayhub.backend.repository.RoomRepository;
import com.stayhub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Transactional
    public BookingResponseDTO createBooking(String userId, BookingRequestDTO request) {
        logger.info("Starting booking process for user {} on room {}", userId, request.getRoomId());

        if (request.getCheckIn() == null || request.getCheckOut() == null) {
            throw new IllegalArgumentException("Check-in and check-out dates must not be null");
        }
        if (!request.getCheckIn().isBefore(request.getCheckOut())) {
            throw new IllegalArgumentException("Check-in date must be before check-out date");
        }
        if (request.getCheckIn().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Check-in date cannot be in the past");
        }

        // Lock Room and verify existence
        Room room = roomRepository.findByIdForUpdate(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + request.getRoomId()));

        // Pessimistic Write Lock on existing bookings
        List<Booking> overlapping = bookingRepository.findOverlappingBookingsForUpdate(
                request.getRoomId(), request.getCheckIn(), request.getCheckOut());

        if (!overlapping.isEmpty()) {
            logger.warn("Room {} is already booked for overlapping dates", request.getRoomId());
            throw new RoomNotAvailableException("Room is not available for the selected dates");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        long nights = ChronoUnit.DAYS.between(request.getCheckIn(), request.getCheckOut());
        BigDecimal totalAmount = room.getPricePerNight().multiply(BigDecimal.valueOf(nights));

        // Create temporary Booking
        Booking booking = Booking.builder()
                .user(user)
                .room(room)
                .checkIn(request.getCheckIn())
                .checkOut(request.getCheckOut())
                .totalPrice(totalAmount)
                .status(BookingStatus.PENDING)
                .build();
        booking = bookingRepository.save(booking);

        // Create temporary Payment record
        Payment payment = Payment.builder()
                .booking(booking)
                .paymentMethod(request.getPaymentMethod())
                .amount(totalAmount)
                .status(PaymentStatus.PENDING)
                .build();
        payment = paymentRepository.save(payment);

        // Process payment
        boolean paymentSuccess = false;
        try {
            paymentSuccess = paymentService.processPayment(totalAmount, request.getPaymentMethod(), request.getPaymentDetails());
        } catch (Exception e) {
            logger.error("Payment processing error. Rolling back booking creation.", e);
            throw e;
        }

        if (!paymentSuccess) {
            logger.error("Payment failed. Rolling back booking creation.");
            throw new PaymentFailedException("Payment processing failed");
        }

        // Finalize transaction records
        booking.setStatus(BookingStatus.CONFIRMED);
        payment.setStatus(PaymentStatus.COMPLETED);
        
        if (request.getPaymentDetails() != null && request.getPaymentDetails().getPaypalOrderId() != null) {
            payment.setTransactionReference(request.getPaymentDetails().getPaypalOrderId());
        }

        bookingRepository.save(booking);
        paymentRepository.save(payment);

        logger.info("Booking created successfully with ID {}", booking.getId());
        return DtoMapper.toDto(booking, payment);
    }

    @Transactional(readOnly = true)
    public List<BookingResponseDTO> getBookingsByUserId(String userId) {
        return bookingRepository.findByUserId(userId).stream()
                .map(booking -> {
                    Payment payment = paymentRepository.findByBookingId(booking.getId()).orElse(null);
                    return DtoMapper.toDto(booking, payment);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BookingResponseDTO> getAllBookings() {
        return bookingRepository.findAll().stream()
                .map(booking -> {
                    Payment payment = paymentRepository.findByBookingId(booking.getId()).orElse(null);
                    return DtoMapper.toDto(booking, payment);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BookingResponseDTO> getBookingsByOwnerId(String ownerId) {
        return bookingRepository.findByRoomOwnerId(ownerId).stream()
                .map(booking -> {
                    Payment payment = paymentRepository.findByBookingId(booking.getId()).orElse(null);
                    return DtoMapper.toDto(booking, payment);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public BookingResponseDTO cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + bookingId));
        
        booking.setStatus(BookingStatus.CANCELLED);
        booking = bookingRepository.save(booking);

        Payment payment = paymentRepository.findByBookingId(bookingId).orElse(null);
        return DtoMapper.toDto(booking, payment);
    }
}
