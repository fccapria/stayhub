package com.stayhub.backend.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebComponentConfig {

    @Bean
    public FilterRegistrationBean<DiagnosticFilter> loggingFilter() {
        FilterRegistrationBean<DiagnosticFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new DiagnosticFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }

    @Bean
    public ServletRegistrationBean<ReceiptExportServlet> receiptServletRegistration(ReceiptExportServlet receiptServlet) {
        ServletRegistrationBean<ReceiptExportServlet> registrationBean = new ServletRegistrationBean<>(
                receiptServlet, "/api/v1/exports/receipt"
        );
        registrationBean.setLoadOnStartup(1);
        return registrationBean;
    }
}
