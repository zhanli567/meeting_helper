package com.company.meetinghelper.common.exception;

import com.company.meetinghelper.common.api.ApiResponse;
import com.company.meetinghelper.common.logging.ApiAccessLogFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Represents the global exception handler class.
 */
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

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        String message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> leafName(violation.getPropertyPath().toString())
                        + "：" + violation.getMessage())
                .orElse("请求参数不正确");
        return validationFailure(exception, request, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        String message = exception.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> Objects.toString(
                                result.getMethodParameter().getParameterName(),
                                "请求参数"
                        ) + "：" + Objects.toString(error.getDefaultMessage(), "请求参数不正确")))
                .findFirst()
                .orElse("请求参数不正确");
        return validationFailure(exception, request, message);
    }

    private ResponseEntity<ApiResponse<Void>> validationFailure(
            Exception exception,
            HttpServletRequest request,
            String message
    ) {
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

    private String leafName(String propertyPath) {
        int separator = propertyPath.lastIndexOf('.');
        return separator < 0 ? propertyPath : propertyPath.substring(separator + 1);
    }
}
