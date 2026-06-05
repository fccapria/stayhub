package com.stayhub.backend.payment;

import com.stayhub.backend.dto.PaymentDetailsDTO;
import java.math.BigDecimal;

public interface PaymentStrategy {
    boolean processPayment(BigDecimal amount, PaymentDetailsDTO details);
}
