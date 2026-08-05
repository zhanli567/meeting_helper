package com.company.meetinghelper.agent.tool;

import java.util.Map;

/** 描述一个可供智能体调用的工具契约。
 *
 * @param name 工具名称
 * @param description 工具描述
 * @param sideEffect 副作用级别
 * @param riskLevel 风险级别
 * @param requiresConfirmation 是否需要确认
 * @param idempotent 是否幂等
 * @param inputSchema 输入参数Schema
 */
public record AgentToolDefinition(
        String name,
        String description,
        AgentToolSideEffect sideEffect,
        String riskLevel,
        boolean requiresConfirmation,
        boolean idempotent,
        Map<String, Object> inputSchema
) {
}
