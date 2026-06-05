package com.stayhub.backend.payment;

import com.stayhub.backend.dto.PaymentDetailsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Map;

@Component("PAYPAL")
public class PayPalPaymentStrategy implements PaymentStrategy {

    private static final Logger logger = LoggerFactory.getLogger(PayPalPaymentStrategy.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${paypal.client-id:mock_client_id}")
    private String clientId;

    @Value("${paypal.client-secret:mock_client_secret}")
    private String clientSecret;

    @Value("${paypal.api-url:https://api-m.sandbox.paypal.com}")
    private String apiUrl;

    @Override
    public boolean processPayment(BigDecimal amount, PaymentDetailsDTO details) {
        logger.info("Processing PayPal payment of {} for order ID {}", amount, details != null ? details.getPaypalOrderId() : null);

        if (details == null || details.getPaypalOrderId() == null || details.getPaypalOrderId().isBlank()) {
            logger.error("PayPal Order ID is missing");
            return false;
        }

        // If we are in mock mode
        if ("mock_client_id".equals(clientId) || details.getPaypalOrderId().startsWith("MOCK-")) {
            logger.info("Credentials not set or mock order ID used, simulating successful PayPal transaction");
            return true;
        }

        try {
            String accessToken = getAccessToken();
            if (accessToken == null) {
                logger.error("Failed to retrieve PayPal access token");
                return false;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>("{}", headers);

            String captureUrl = apiUrl + "/v2/checkout/orders/" + details.getPaypalOrderId() + "/capture";
            ResponseEntity<Map> response = restTemplate.postForEntity(captureUrl, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.CREATED || response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> body = response.getBody();
                if (body != null && "COMPLETED".equals(body.get("status"))) {
                    logger.info("PayPal capture completed successfully for order {}", details.getPaypalOrderId());
                    return true;
                }
            }
            logger.error("PayPal capture failed. Status: {}", response.getStatusCode());
            return false;
        } catch (Exception e) {
            logger.error("Exception during PayPal payment capture", e);
            return false;
        }
    }

    private String getAccessToken() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String auth = clientId + ":" + clientSecret;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);

            HttpEntity<String> entity = new HttpEntity<>("grant_type=client_credentials", headers);
            String tokenUrl = apiUrl + "/v1/oauth2/token";
            
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
        } catch (Exception e) {
            logger.error("Error fetching PayPal OAuth2 token", e);
        }
        return null;
    }
}
