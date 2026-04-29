package com.yingshi.server.common.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode errorCode;
    private final Object details;

    public ApiException(HttpStatus status, ErrorCode errorCode, String message) {
        this(status, errorCode, message, null);
    }

    public ApiException(HttpStatus status, ErrorCode errorCode, String message, Object details) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.details = details;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object getDetails() {
        return details;
    }
}
