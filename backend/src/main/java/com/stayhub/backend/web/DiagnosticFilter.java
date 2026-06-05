package com.stayhub.backend.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

public class DiagnosticFilter extends HttpFilter {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticFilter.class);

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        long startTime = System.currentTimeMillis();
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put("requestId", requestId);
        MDC.put("method", request.getMethod());
        MDC.put("uri", request.getRequestURI());

        logger.info("Incoming request: {} {}", request.getMethod(), request.getRequestURI());

        try {
            chain.doFilter(request, response);
        } finally {
            long latency = System.currentTimeMillis() - startTime;
            logger.info("Request completed. Latency: {} ms. Status: {}", latency, response.getStatus());
            MDC.clear();
        }
    }
}
