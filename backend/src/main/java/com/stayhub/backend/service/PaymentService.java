package com.stayhub.backend.service;

import com.stayhub.backend.dto.PaymentDetailsDTO;
import com.stayhub.backend.entity.PaymentMethod;
import com.stayhub.backend.exception.PaymentFailedException;
import com.stayhub.backend.payment.PaymentStrategy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final Map<String, PaymentStrategy> paymentStrategies;

    public boolean processPayment(BigDecimal amount, PaymentMethod method, PaymentDetailsDTO details) {
        String strategyKey = method.name();
        PaymentStrategy strategy = paymentStrategies.get(strategyKey);

        if (strategy == null) {
            logger.error("Payment strategy not found for method: {}", strategyKey);
            throw new PaymentFailedException("Unsupported payment method: " + strategyKey);
        }

        logger.info("Routing payment request of {} to strategy: {}", amount, strategyKey);
        return strategy.processPayment(amount, details);
    }
}
