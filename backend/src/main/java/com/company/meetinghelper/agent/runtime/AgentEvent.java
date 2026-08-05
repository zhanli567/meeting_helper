package com.company.meetinghelper.agent.runtime;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 智能体事件统一信封。
 *
 * @param runId 运行标识
 * @param conversationId 会话标识
 * @param eventId 事件标识
 * @param stepNo 步骤序号
 * @param type 事件类型
 * @param payload 事件载荷
 * @param timestamp 事件时间
 */
public record AgentEvent(
        String runId,
        String conversationId,
        String eventId,
        int stepNo,
        AgentEventType type,
        Map<String, Object> payload,
        OffsetDateTime timestamp
) {
}
