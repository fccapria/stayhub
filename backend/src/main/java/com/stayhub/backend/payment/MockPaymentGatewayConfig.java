package com.stayhub.backend.payment;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@Configuration
@Profile("!prod")
public class MockPaymentGatewayConfig {

    private static final Logger logger = LoggerFactory.getLogger(MockPaymentGatewayConfig.class);

    @Value("${wiremock.server.port:8089}")
    private int wiremockPort;

    private WireMockServer wireMockServer;

    @PostConstruct
    public void startWireMock() {
        logger.info("Initializing programmatic WireMock server on port {}", wiremockPort);
        wireMockServer = new WireMockServer(wiremockPort);
        try {
            wireMockServer.start();
            logger.info("WireMock server started successfully on port {}", wiremockPort);
        } catch (Exception e) {
            logger.warn("WireMock server failed to start (likely already running on port {}): {}", wiremockPort, e.getMessage());
        }
        WireMock.configureFor("localhost", wiremockPort);

        // Default Stub: Generic Fallback Approval (registered first so specific stubs take precedence)
        stubFor(post(urlEqualTo("/api/v1/payments/card"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"APPROVED\", \"authCode\": \"8A2F19\"}")));

        // Stub 1: Approved Transaction
        stubFor(post(urlEqualTo("/api/v1/payments/card"))
                .withRequestBody(matchingJsonPath("$.cardNumber", matching(".*0000")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"APPROVED\", \"authCode\": \"A7C491\"}")));

        // Stub 2: Declined Transaction
        stubFor(post(urlEqualTo("/api/v1/payments/card"))
                .withRequestBody(matchingJsonPath("$.cardNumber", matching(".*4444")))
                .willReturn(aResponse()
                        .withStatus(402)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"DECLINED\", \"reason\": \"Card declined: Insufficient funds\"}")));

        // Stub 3: Network Timeout (5 seconds delay)
        stubFor(post(urlEqualTo("/api/v1/payments/card"))
                .withRequestBody(matchingJsonPath("$.cardNumber", matching(".*9999")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(5000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"APPROVED\", \"authCode\": \"99D0F1\"}")));

        logger.info("WireMock stubs initialized successfully on port {}", wiremockPort);
    }

    @PreDestroy
    public void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            logger.info("Stopping WireMock server");
            wireMockServer.stop();
        }
    }
}
