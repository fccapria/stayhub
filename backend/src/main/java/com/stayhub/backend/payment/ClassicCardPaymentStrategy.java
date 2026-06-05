package com.stayhub.backend.payment;

import com.stayhub.backend.dto.PaymentDetailsDTO;
import com.stayhub.backend.exception.PaymentFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component("CLASSIC_CARD")
public class ClassicCardPaymentStrategy implements PaymentStrategy {

    private static final Logger logger = LoggerFactory.getLogger(ClassicCardPaymentStrategy.class);

    private final RestTemplate restTemplate;

    @Value("${wiremock.server.port:8089}")
    private int wiremockPort;

    public ClassicCardPaymentStrategy() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public boolean processPayment(BigDecimal amount, PaymentDetailsDTO details) {
        logger.info("Processing classic card payment of {} for card ending in {}", 
                amount, 
                (details != null && details.getCardNumber() != null && details.getCardNumber().length() > 4) 
                        ? details.getCardNumber().substring(details.getCardNumber().length() - 4) 
                        : "unknown");

        if (details == null || details.getCardNumber() == null || details.getCardNumber().isBlank()) {
            logger.error("Credit card details are missing");
            throw new PaymentFailedException("Credit card details are missing");
        }

        String requestUrl = "http://localhost:" + wiremockPort + "/api/v1/payments/card";

        Map<String, Object> requestPayload = new HashMap<>();
        requestPayload.put("amount", amount);
        requestPayload.put("cardNumber", details.getCardNumber());
        requestPayload.put("cardHolder", details.getCardHolder());
        requestPayload.put("cvv", details.getCvv());
        requestPayload.put("expirationDate", details.getExpirationDate());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(requestUrl, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String status = (String) response.getBody().get("status");
                if ("APPROVED".equals(status)) {
                    String ref = (String) response.getBody().get("transactionReference");
                    logger.info("Card transaction approved successfully: reference {}", ref);
                    details.setPaypalOrderId(ref);
                    return true;
                }
            }
            logger.error("Card transaction not approved. Status: {}", response.getStatusCode());
            throw new PaymentFailedException("Payment transaction not approved");
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("Payment gateway error response: {}", e.getResponseBodyAsString());
            throw new PaymentFailedException("Payment gateway declined transaction: " + e.getMessage());
        } catch (ResourceAccessException e) {
            logger.error("Connection or read timeout occurred with banking gateway. Rollback required.", e);
            throw new PaymentFailedException("Banking API connection timeout. Payment state uncertain; transaction rolled back.");
        } catch (Exception e) {
            logger.error("General payment processing error", e);
            throw new PaymentFailedException("Failed to process classic card payment: " + e.getMessage());
        }
    }
}
