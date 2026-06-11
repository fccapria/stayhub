package com.stayhub.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    @Value("${paypal.client-id:mock_client_id}")
    private String paypalClientId;

    @GetMapping("/paypal")
    public Map<String, String> getPayPalConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("clientId", paypalClientId);
        return config;
    }
}
