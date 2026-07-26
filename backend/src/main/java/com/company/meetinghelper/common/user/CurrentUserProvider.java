package com.company.meetinghelper.common.user;

import com.company.meetinghelper.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {
    private static final String HEADER = "X-User-Id";

    private final HttpServletRequest request;

    /**
     * 创建当前用户提供器。
     *
     * @param request 当前HTTP请求
     */
    public CurrentUserProvider(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * 获取当前请求中的用户ID。
     *
     * @return 去除首尾空白后的用户ID
     */
    public String requireUserId() {
        var userId = request.getHeader(HEADER);
        if (userId == null || userId.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "缺少当前用户信息");
        }
        return userId.trim();
    }
}
