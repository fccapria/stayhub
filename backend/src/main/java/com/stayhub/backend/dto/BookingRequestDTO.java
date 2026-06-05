package com.stayhub.backend.dto;

import com.stayhub.backend.entity.PaymentMethod;
import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingRequestDTO {
    private Long roomId;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private PaymentMethod paymentMethod;
    private PaymentDetailsDTO paymentDetails;
}
