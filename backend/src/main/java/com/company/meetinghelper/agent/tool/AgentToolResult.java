package com.company.meetinghelper.agent.tool;

/** 工具调用的统一结果。
 *
 * @param callId 调用ID
 * @param toolName 工具名称
 * @param success 是否成功
 * @param data 成功时的结果数据
 * @param errorCode 失败时的错误码
 * @param message 结果消息
 */
public record AgentToolResult(
        String callId,
        String toolName,
        boolean success,
        Object data,
        String errorCode,
        String message
) {
    /** 创建成功结果。
     *
     * @param callId 调用ID
     * @param toolName 工具名称
     * @param data 结果数据
     * @return 成功结果
     */
    public static AgentToolResult success(String callId, String toolName, Object data) {
        return new AgentToolResult(callId, toolName, true, data, null, null);
    }

    /** 创建失败结果。
     *
     * @param callId 调用ID
     * @param toolName 工具名称
     * @param errorCode 错误码
     * @param message 错误消息
     * @return 失败结果
     */
    public static AgentToolResult failure(String callId, String toolName, String errorCode, String message) {
        return new AgentToolResult(callId, toolName, false, null, errorCode, message);
    }
}
