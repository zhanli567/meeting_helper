package com.company.meetinghelper.agent.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 智能体发起的一次工具调用。
 *
 * @param id 调用ID
 * @param name 工具名称
 * @param arguments 工具参数
 * @param providerContext 模型厂商返回的工具调用上下文字段，用于后续对话原样续传
 */
public record AgentToolCall(
        String id,
        String name,
        Map<String, Object> arguments,
        Map<String, Object> providerContext
) {
    /** 创建不带厂商上下文的工具调用。 *
     * @param id 调用ID
     * @param name 工具名称
     * @param arguments 工具参数
     */
    public AgentToolCall(String id, String name, Map<String, Object> arguments) {
        this(id, name, arguments, Map.of());
    }

    /** 规整可选 Map，避免空指针并保护调用对象不可变。 */
    public AgentToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        providerContext = providerContext == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(providerContext));
    }
}
