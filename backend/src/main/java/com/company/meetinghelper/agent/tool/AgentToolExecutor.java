package com.company.meetinghelper.agent.tool;

import com.company.meetinghelper.agent.tool.query.AgentQueryTools;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 校验并执行智能体允许的只读工具调用。 */
@Component
public class AgentToolExecutor {
    private final AgentToolRegistry registry;
    private final AgentQueryTools queryTools;

    /** 创建只读工具执行器。
     *
     * @param registry 工具注册表
     * @param queryTools 查询工具适配器
     */
    public AgentToolExecutor(AgentToolRegistry registry, AgentQueryTools queryTools) {
        this.registry = registry;
        this.queryTools = queryTools;
    }

    /** 执行一次工具调用。
     *
     * @param meetingId 运行时会议ID
     * @param call 工具调用
     * @return 工具结果
     */
    public AgentToolResult execute(String meetingId, AgentToolCall call) {
        AgentToolDefinition definition = registry.find(call.name());
        if (definition == null || definition.sideEffect() != AgentToolSideEffect.READ) {
            return AgentToolResult.failure(call.id(), call.name(), "TOOL_NOT_ALLOWED", "工具不在只读白名单中");
        }
        Map<String, Object> arguments = call.arguments() == null ? Map.of() : call.arguments();
        Object data = executeReadTool(meetingId, call.name(), arguments);
        return AgentToolResult.success(call.id(), call.name(), data);
    }

    private Object executeReadTool(String meetingId, String name, Map<String, Object> arguments) {
        return switch (name) {
            case "workspace.get_summary" -> queryTools.getSummary(meetingId, arguments);
            case "assignment.list_unassigned" -> queryTools.listUnassigned(meetingId, arguments);
            case "participant.search" -> queryTools.searchParticipants(meetingId, arguments);
            case "seat.search" -> queryTools.searchSeats(meetingId, arguments);
            default -> throw new IllegalStateException("未注册的只读工具: " + name);
        };
    }
}
