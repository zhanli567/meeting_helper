package com.company.meetinghelper.agent.provider;

import com.company.meetinghelper.agent.tool.AgentToolCall;

/** provider 输出的归一化结果。 *
 * @param assistantText 助手文本
 * @param toolCall 工具调用
 * @param done 是否结束
 * @param errorCode 错误码
 * @param errorMessage 错误信息
 */
public record AgentProviderResponse(
        String assistantText,
        AgentToolCall toolCall,
        boolean done,
        String errorCode,
        String errorMessage
) {
    /** 创建工具调用响应。 *
     * @param toolCall 工具调用
     * @return 响应
     */
    public static AgentProviderResponse toolCall(AgentToolCall toolCall) {
        return new AgentProviderResponse(null, toolCall, false, null, null);
    }

    /** 创建完成文本响应。 *
     * @param text 助手文本
     * @return 响应
     */
    public static AgentProviderResponse text(String text) {
        return new AgentProviderResponse(text, null, true, null, null);
    }

    /** 创建错误响应。 *
     * @param code 错误码
     * @param message 错误信息
     * @return 响应
     */
    public static AgentProviderResponse error(String code, String message) {
        return new AgentProviderResponse(null, null, true, code, message);
    }
}
