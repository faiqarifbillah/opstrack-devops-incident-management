package com.opstrack.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import tools.jackson.databind.ObjectMapper;
import com.opstrack.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletResponse;

/** Filters run before Spring MVC, so they write their error responses directly. */
final class ErrorWriter {

    private ErrorWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status,
            String message, String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.of(status.value(), status.getReasonPhrase(), message, path));
    }
}
