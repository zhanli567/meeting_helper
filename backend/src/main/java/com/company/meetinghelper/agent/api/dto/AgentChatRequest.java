package com.company.meetinghelper.agent.api.dto;

/**
 * 智能体聊天请求。
 *
 * @param conversationId 会话标识
 * @param meetingId 会议标识
 * @param workspaceRevision 工作区版本
 * @param message 用户消息
 * @param stream 是否流式返回
 * @param mode 工作模式
 */
public record AgentChatRequest(
        String conversationId,
        String meetingId,
        String workspaceRevision,
        String message,
        boolean stream,
        AgentMode mode
) {
}
