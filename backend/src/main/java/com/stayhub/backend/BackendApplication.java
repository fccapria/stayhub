package com.stayhub.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@SpringBootApplication
@EnableAsync
public class BackendApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(BackendApplication.class, args);
    }

    private static void loadDotEnv() {
        java.io.File envFile = new java.io.File(".env");
        if (!envFile.exists()) {
            envFile = new java.io.File("../.env");
        }
        if (envFile.exists()) {
            try {
                List<String> lines = Files.readAllLines(Paths.get(envFile.getAbsolutePath()));
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int eqIdx = line.indexOf('=');
                    if (eqIdx > 0) {
                        String key = line.substring(0, eqIdx).trim();
                        String value = line.substring(eqIdx + 1).trim();
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        } else if (value.startsWith("'") && value.endsWith("'")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if ("SPRING_MAIL_PASSWORD".equalsIgnoreCase(key)) {
                            value = value.replace(" ", "").trim();
                            System.setProperty("spring.mail.password", value);
                        }
                        if ("SPRING_MAIL_USERNAME".equalsIgnoreCase(key)) {
                            System.setProperty("spring.mail.username", value);
                        }
                        if ("STAYHUB_MAIL_FROM".equalsIgnoreCase(key)) {
                            System.setProperty("stayhub.mail.from", value);
                        }
                        if ("PAYPAL_CLIENT_ID".equalsIgnoreCase(key)) {
                            System.setProperty("paypal.client-id", value);
                        }
                        if ("PAYPAL_CLIENT_SECRET".equalsIgnoreCase(key)) {
                            System.setProperty("paypal.client-secret", value);
                        }
                        System.setProperty(key, value);
                        System.out.println("Loaded env property: " + key);
                    }
                }
            } catch (IOException e) {
                System.err.println("Could not load .env file: " + e.getMessage());
            }
        }
    }
}
