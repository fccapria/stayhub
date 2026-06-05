package com.stayhub.backend.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDetailsDTO {
    private String cardHolder;
    private String cardNumber;
    private String cvv;
    private String expirationDate;
    private String paypalEmail;
    private String paypalOrderId;
}
