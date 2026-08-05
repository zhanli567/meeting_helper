package com.company.meetinghelper.agent.provider;

import com.company.meetinghelper.agent.api.dto.AgentChatRequest;
import com.company.meetinghelper.agent.tool.AgentToolDefinition;
import com.company.meetinghelper.agent.tool.AgentToolObservation;
import com.company.meetinghelper.agent.tool.AgentToolResult;
import java.util.List;

/** provider 单步调用所需的最小上下文。
 *
 * @param chatRequest 聊天请求
 * @param toolResults 当前运行中已经产生的工具结果
 * @param toolDefinitions 当前允许 provider 使用的工具定义
 * @param toolObservations 当前运行中真实工具调用和结果的配对观察历史
 */
public record AgentProviderRequest(
        AgentChatRequest chatRequest,
        List<AgentToolResult> toolResults,
        List<AgentToolDefinition> toolDefinitions,
        List<AgentToolObservation> toolObservations
) {
    /** 创建没有工具结果、工具定义和观察历史的初始请求。
     *
     * @param chatRequest 聊天请求
     */
    public AgentProviderRequest(AgentChatRequest chatRequest) {
        this(chatRequest, List.of(), List.of(), List.of());
    }

    /** 创建没有工具定义和观察历史的兼容请求。
     *
     * @param chatRequest 聊天请求
     * @param toolResults 工具结果
     */
    public AgentProviderRequest(AgentChatRequest chatRequest, List<AgentToolResult> toolResults) {
        this(chatRequest, toolResults, List.of(), List.of());
    }

    /** 创建包含工具定义但不包含观察历史的兼容请求。
     *
     * @param chatRequest 聊天请求
     * @param toolResults 工具结果
     * @param toolDefinitions provider 可用工具定义
     */
    public AgentProviderRequest(AgentChatRequest chatRequest, List<AgentToolResult> toolResults,
                                List<AgentToolDefinition> toolDefinitions) {
        this(chatRequest, toolResults, toolDefinitions, List.of());
    }
}
