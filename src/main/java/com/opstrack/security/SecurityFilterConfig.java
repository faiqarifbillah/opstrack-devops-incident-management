package com.opstrack.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.ObjectMapper;

/**
 * Applies both filters to /api/*. Actuator paths are outside that pattern, so
 * /actuator/health stays public. The servlet container matches the pattern after
 * decoding the URL, so an encoded path such as /%61pi/incidents is still covered.
 */
@Configuration
public class SecurityFilterConfig {

    private static final String PROTECTED_PATHS = "/api/*";

    /** Runs before the other filters, so their 401 and 429 responses get the headers too. */
    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        FilterRegistrationBean<SecurityHeadersFilter> registration =
                new FilterRegistrationBean<>(new SecurityHeadersFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(0);
        return registration;
    }

    /** Runs before the key check, so repeated guesses at the API key are limited too. */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            @Value("${opstrack.security.rate-limit-per-minute}") int requestsPerMinute,
            ObjectMapper objectMapper) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
                new RateLimitFilter(new RateLimiter(requestsPerMinute), objectMapper));
        registration.addUrlPatterns(PROTECTED_PATHS);
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(
            @Value("${opstrack.security.api-key}") String apiKey,
            ObjectMapper objectMapper) {
        FilterRegistrationBean<ApiKeyFilter> registration = new FilterRegistrationBean<>(
                new ApiKeyFilter(apiKey, objectMapper));
        registration.addUrlPatterns(PROTECTED_PATHS);
        registration.setOrder(2);
        return registration;
    }
}
