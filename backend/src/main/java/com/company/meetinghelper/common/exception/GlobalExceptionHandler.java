package com.company.meetinghelper.common.exception;

import com.company.meetinghelper.common.api.ApiResponse;
import com.company.meetinghelper.common.logging.ApiAccessLogFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> handleApiException(
            ApiException exception,
            HttpServletRequest request
    ) {
        LOGGER.warn(
                "[API][EXCEPTION] requestId={} method={} path={} status={} exception={} message={}",
                request.getAttribute(ApiAccessLogFilter.REQUEST_ID_ATTRIBUTE),
                request.getMethod(),
                request.getRequestURI(),
                exception.getStatus().value(),
                exception.getClass().getSimpleName(),
                exception.getMessage()
        );
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResponse.failure(exception.getStatus().value(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + "：" + error.getDefaultMessage())
                .orElse("请求参数不正确");
        LOGGER.warn(
                "[API][EXCEPTION] requestId={} method={} path={} status={} exception={} message={}",
                request.getAttribute(ApiAccessLogFilter.REQUEST_ID_ATTRIBUTE),
                request.getMethod(),
                request.getRequestURI(),
                HttpStatus.BAD_REQUEST.value(),
                exception.getClass().getSimpleName(),
                message
        );
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(HttpStatus.BAD_REQUEST.value(), message));
    }
}
