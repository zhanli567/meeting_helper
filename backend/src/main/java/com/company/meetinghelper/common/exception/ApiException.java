package com.company.meetinghelper.common.exception;

import org.springframework.http.HttpStatus;

/**
 * ApiException 类。
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

/**
 * getStatus 方法。
 * @return 返回结果。
 */
public HttpStatus getStatus() {
        return status;
    }
}

