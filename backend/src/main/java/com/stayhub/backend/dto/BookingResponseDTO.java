package com.stayhub.backend.dto;

import com.stayhub.backend.entity.BookingStatus;
import com.stayhub.backend.entity.PaymentStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingResponseDTO {
    private Long id;
    private String userId;
    private Long roomId;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private BigDecimal totalPrice;
    private BookingStatus status;
    private PaymentStatus paymentStatus;
    private String transactionReference;
}
