package com.ticktit.common.exception;

import com.ticktit.common.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Our own errors: thrown on purpose by services. Expected, so no stack trace.
    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        log.info("Request failed: {}", ex.getErrorCode());
        return createProblemDetails(ex.getErrorCode());
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
