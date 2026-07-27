package com.company.meetinghelper.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class ApiAccessLogFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_ATTRIBUTE = ApiAccessLogFilter.class.getName() + ".requestId";

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiAccessLogFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        long startNanos = System.nanoTime();
        String method = request.getMethod();
        String path = request.getRequestURI();
        String query = request.getQueryString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);

        LOGGER.info(
                "[API][START] requestId={} method={} path={} query={} remote={}",
                requestId,
                method,
                path,
                query == null ? "" : query,
                request.getRemoteAddr()
        );

        try {
            filterChain.doFilter(request, response);
        } catch (Exception exception) {
            LOGGER.error(
                    "[API][EXCEPTION] requestId={} method={} path={} status={} exception={} message={}",
                    requestId,
                    method,
                    path,
                    response.getStatus(),
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    exception
            );
            throw exception;
        } finally {
            long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
            LOGGER.info(
                    "[API][RESULT] requestId={} method={} path={} status={} durationMs={}",
                    requestId,
                    method,
                    path,
                    response.getStatus(),
                    durationMillis
            );
        }
    }
}
