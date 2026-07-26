package com.company.meetinghelper.common.api;

/**
 * 公司前后端约定的统一JSON响应。
 *
 * @param code 业务状态码，0表示成功
 * @param data 响应数据
 * @param msg 响应说明
 * @param <T> 响应数据类型
 */
public record ApiResponse<T>(int code, T data, String msg) {

    /**
     * 创建成功响应。
     *
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 统一成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, data, "success");
    }

    /**
     * 创建失败响应。
     *
     * @param code 非零业务状态码
     * @param message 错误说明
     * @return 统一失败响应
     */
    public static ApiResponse<Void> failure(int code, String message) {
        return new ApiResponse<>(code, null, message);
    }
}
