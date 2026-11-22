package com.payrecon.payment.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

// Every error → a clean ProblemDetail JSON.
//
// We EXTEND ResponseEntityExceptionHandler: Spring then handles ~20 standard errors itself with the
// right status (405 wrong method, 415 wrong body type, 400 missing header, 404 unknown URL, our own
// ResponseStatusException 404/409 ...). Before, our catch-all turned some of those into 500.
//
// For the standard errors where we want OUR wording, we OVERRIDE Spring's method (not @ExceptionHandler,
// which would clash with Spring's own handler for the same exception).
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // A @Valid rule failed (e.g. negative amount) → 400 with one message per field
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more fields are invalid");
        pd.setTitle("Validation failed");
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().headers(headers).body(pd);
    }

    // Body isn't valid JSON, or a value has the wrong type ("amount": "abc") → 400
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON");
        pd.setTitle("Malformed request");
        return ResponseEntity.badRequest().headers(headers).body(pd);
    }

    // Bad value in the URL (e.g. /payments/not-a-uuid or ?status=WRONG) → 400
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name = ex.getPropertyName() != null ? ex.getPropertyName() : "parameter";
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "'" + ex.getValue() + "' is not a valid " + name);
        pd.setTitle("Invalid parameter");
        return ResponseEntity.badRequest().headers(headers).body(pd);
    }

    // Wrong username or password on /auth/login → 401 (never say WHICH one was wrong)
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleLoginFailed(AuthenticationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Invalid username or password");
        pd.setTitle("Login failed");
        return pd;
    }

    // Redis down or not answering → 503, temporary, the client should retry later
    @ExceptionHandler({RedisConnectionFailureException.class, QueryTimeoutException.class})
    public ProblemDetail handleRedisDown(Exception ex) {
        log.error("Redis unavailable", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Payments are temporarily unavailable. Please try again shortly.");
        pd.setTitle("Service unavailable");
        return pd;
    }

    // Anything unexpected: log the details, but never show them to the client → 500
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later.");
        pd.setTitle("Internal error");
        return pd;
    }
}
