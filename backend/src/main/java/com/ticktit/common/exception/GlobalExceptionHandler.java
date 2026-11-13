package com.ticktit.common.exception;

import com.ticktit.common.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Our own errors: thrown on purpose by services. Expected, so no stack trace.
    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        log.info("Request failed: {}", ex.getErrorCode());
        ProblemDetail problemDetail = createProblemDetails(ex.getErrorCode());
        ex.getDetails().forEach(problemDetail::setProperty);
        return problemDetail;
    }

    // Missing, bad or expired JWT (forwarded here from SecurityConfig)
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return createProblemDetails(ErrorCode.UNAUTHORIZED);
    }

    // Valid JWT but wrong role (URL rule in SecurityConfig, or @PreAuthorize on a method)
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return createProblemDetails(ErrorCode.FORBIDDEN);
    }

    // 2. @Valid failed on a request body
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        // never log ex or ex.getMessage() here: it contains the rejected values, e.g. passwords
        log.debug("Validation failed on {} field(s)", ex.getBindingResult().getFieldErrorCount());
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage()); // first message per field
        }
        ProblemDetail problemDetail = createProblemDetails(ErrorCode.VALIDATION_FAILED);
        problemDetail.setProperty("errors", errors);
        return problemDetail;
    }

    // Client mistakes Spring itself detects. Without these the catch-all below would turn them into 500s.
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ProblemDetail handleNoResource(Exception ex) {
        return createProblemDetails(ErrorCode.NOT_FOUND);                 // unknown URL
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return createProblemDetails(ErrorCode.METHOD_NOT_ALLOWED);        // e.g. GET on a POST-only URL
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,            // broken JSON body
            MissingServletRequestParameterException.class,               // required ?param missing
            MethodArgumentTypeMismatchException.class,                   // ?id=abc where a number is expected
            HttpMediaTypeNotSupportedException.class})                   // body sent without Content-Type: application/json
    public ProblemDetail handleMalformedRequest(Exception ex) {
        return createProblemDetails(ErrorCode.MALFORMED_REQUEST);
    }

    // 3. Anything we did not expect: NullPointerException, bug, db down etc.
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected Exception: {}", ex.getMessage(), ex);
        return createProblemDetails(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    // One Place that turns an ErrorCode into a ProblemDetail response
    private ProblemDetail createProblemDetails(ErrorCode errorCode) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(errorCode.getHttpStatus(), errorCode.getMessage());
        problemDetail.setProperty("code", errorCode.name());
        problemDetail.setProperty("requestId", MDC.get("requestId"));
        return problemDetail;
    }
}
