package com.opstrack.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Rejects requests that do not carry the correct X-API-Key header. */
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    static final int MIN_KEY_LENGTH = 32;

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    private final byte[] expectedKey;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(String apiKey, ObjectMapper objectMapper) {
        // Stop at startup rather than run with a missing or guessable key.
        // The message deliberately never includes the configured value.
        if (apiKey == null || apiKey.length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    "OPSTRACK_API_KEY must be set to at least " + MIN_KEY_LENGTH + " characters");
        }
        this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String providedKey = request.getHeader(HEADER);
        if (providedKey != null && matches(providedKey)) {
            chain.doFilter(request, response);
            return;
        }
        // Log who was rejected, never the key they sent.
        log.warn("Rejected request from {}: {} API key", request.getRemoteAddr(),
                providedKey == null ? "missing" : "invalid");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "ApiKey");
        ErrorWriter.write(response, objectMapper, HttpStatus.UNAUTHORIZED,
                "Missing or invalid API key", request.getRequestURI());
    }

    /** Takes the same time wherever the bytes differ, so timing reveals nothing about the key. */
    private boolean matches(String providedKey) {
        return MessageDigest.isEqual(expectedKey, providedKey.getBytes(StandardCharsets.UTF_8));
    }
}
