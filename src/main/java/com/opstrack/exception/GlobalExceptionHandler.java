package com.opstrack.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.opstrack.dto.ErrorResponse;

/**
 * Turns every exception into an ErrorResponse. The parent class already maps
 * Spring's own exceptions (405, 415, unknown path) to the right status code.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IncidentNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(IncidentNotFoundException ex, WebRequest request) {
        return respond(ex, HttpStatus.NOT_FOUND, ex.getMessage(), null, new HttpHeaders(), request);
    }

    /** Last resort: log the detail for operators, tell the client nothing internal. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        return respond(ex, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", null,
                new HttpHeaders(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return respond(ex, status, "Validation failed", fieldErrors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respond(ex, status, "Request body is missing, is not valid JSON, or has an unknown enum value",
                null, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? "Invalid value for '" + mismatch.getName() + "'"
                : "Invalid request value";
        return respond(ex, status, message, null, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Reached by Spring's own exceptions, whose messages are safe to replace with the status text.
        Object errorBody = body instanceof ErrorResponse ? body : buildBody(status, reason(status), null, request);
        return super.handleExceptionInternal(ex, errorBody, headers, status, request);
    }

    private ResponseEntity<Object> respond(Exception ex, HttpStatusCode status, String message,
            Map<String, String> fieldErrors, HttpHeaders headers, WebRequest request) {
        return handleExceptionInternal(ex, buildBody(status, message, fieldErrors, request), headers, status, request);
    }

    private ErrorResponse buildBody(HttpStatusCode status, String message,
            Map<String, String> fieldErrors, WebRequest request) {
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        return new ErrorResponse(Instant.now(), status.value(), reason(status), message, path, fieldErrors);
    }

    private String reason(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "Error";
    }
}
