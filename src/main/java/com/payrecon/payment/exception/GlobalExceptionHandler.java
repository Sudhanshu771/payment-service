package com.payrecon.payment.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;                          // NEW
import org.springframework.data.redis.RedisConnectionFailureException;         // NEW
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;       // NEW

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 1. A @Valid rule failed (e.g. negative amount) → 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "One or more fields are invalid");
        pd.setTitle("Validation failed");
        pd.setProperty("errors", errors);
        return pd;
    }

    // 2. The body isn't valid JSON, or a value has the wrong type ("amount": "abc") → 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON");
        pd.setTitle("Malformed request");
        return pd;
    }

    // 3. Bad value in the URL (e.g. /payments/not-a-uuid) → 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "'" + ex.getValue() + "' is not a valid " + ex.getName());
        pd.setTitle("Invalid parameter");
        return pd;
    }

    // 4. Required header missing (e.g. no Idempotency-Key) → 400
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Header '" + ex.getHeaderName() + "' is required");
        pd.setTitle("Missing header");
        return pd;
    }

    // 5. NEW: unknown URL (e.g. /favicon.ico or a typo) → 404, not 500
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNotFound(NoResourceFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "No endpoint found for " + ex.getResourcePath());
        pd.setTitle("Not found");
        return pd;
    }

    // 6. Errors we throw on purpose: 404 "Payment not found", 409 idempotency conflicts
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        return ex.getBody();
    }

    // 7. NEW: Redis down or not answering → 503, temporary, the client should retry later
    @ExceptionHandler({RedisConnectionFailureException.class, QueryTimeoutException.class})
    public ProblemDetail handleRedisDown(Exception ex) {
        log.error("Redis unavailable", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Payments are temporarily unavailable. Please try again shortly.");
        pd.setTitle("Service unavailable");
        return pd;
    }

    // 8. Anything unexpected: log the details, but never show them to the client → 500
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later.");
        pd.setTitle("Internal error");
        return pd;
    }
}