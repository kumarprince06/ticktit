package com.ticktit.common.exception;

import com.ticktit.common.enums.ErrorCode;
import lombok.Getter;

import java.util.Map;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    /** Extra fields for the error body, e.g. {"unavailable": ["A1"], "alternative": ["B3","B4"]}. */
    private final Map<String, Object> details;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    public ApiException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = details;
    }
}
