package com.ceawse.onchainindexer.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Configuration
public class MdcFilterConfig {

    @Component
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public static class TraceFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {

            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String traceId = httpRequest.getHeader("X-Trace-Id");

            if (traceId == null || traceId.isEmpty()) {
                traceId = "IDX-" + UUID.randomUUID().toString().substring(0, 8);
            }

            try {
                MDC.put("traceId", traceId);
                chain.doFilter(request, response);
            } finally {
                MDC.clear();
            }
        }
    }
}