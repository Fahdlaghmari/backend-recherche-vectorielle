package com.example.chatbotrag.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus; // Only if needed for status code, else remove
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // Ensure this filter runs before others, especially Spring Security
public class RequestUriFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RequestUriFilter.class);
    private static final int MAX_URI_LENGTH = 2048; // A common limit for URI length

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response, FilterChain chain)
            throws java.io.IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String requestUri = httpRequest.getRequestURI();
        if (requestUri != null && requestUri.length() > MAX_URI_LENGTH) {
            logger.warn("Blocked request with overly long URI ({} characters): {}. Client IP: {}",
                        requestUri.length(), requestUri, httpRequest.getRemoteAddr());
            httpResponse.setStatus(414); // 414 Request-URI Too Long
            httpResponse.setContentType("text/plain;charset=UTF-8");
            httpResponse.getWriter().write("Request URI too long. Maximum allowed length is " + MAX_URI_LENGTH + " characters.");
            return;
        }
        chain.doFilter(request, response);
    }
}
