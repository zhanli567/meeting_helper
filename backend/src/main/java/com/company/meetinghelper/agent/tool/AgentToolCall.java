package com.company.meetinghelper.agent.tool;

import java.util.Map;

/** 智能体发起的一次工具调用。
 *
 * @param id 调用ID
 * @param name 工具名称
 * @param arguments 工具参数
 */
public record AgentToolCall(
        String id,
        String name,
        Map<String, Object> arguments
) {
}
