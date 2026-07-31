package com.company.meetinghelper.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Represents the api exception class.
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

/**
 * Handles get status.
 *
 * @return result
 */
    public HttpStatus getStatus() {
        return status;
    }
}

