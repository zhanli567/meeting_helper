package com.company.meetinghelper.agent.tool;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 管理智能体当前启用的工具白名单。 */
@Component
public class AgentToolRegistry {
    private final List<AgentToolDefinition> definitions = List.of(
            definition("workspace.get_summary", "查询工作区摘要；只读，不改变排座结果。", Map.of()),
            definition("assignment.list_unassigned", "查询未分配座位的参会人；只读，不改变排座结果。", pageSchema()),
            definition("participant.search", "按关键词查询参会人；只读，不改变排座结果。", pageSchema()),
            definition("seat.search", "按关键词查询座位及占用情况；只读，不改变排座结果。", pageSchema())
    );

    /** 返回当前启用的工具定义。
     *
     * @return 工具定义列表
     */
    public List<AgentToolDefinition> enabledDefinitions() {
        return definitions;
    }

    /** 按名称查找工具定义。
     *
     * @param name 工具名称
     * @return 匹配的工具定义，找不到时返回null
     */
    public AgentToolDefinition find(String name) {
        return definitions.stream()
                .filter(definition -> definition.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static AgentToolDefinition definition(
            String name,
            String description,
            Map<String, Object> inputSchema
    ) {
        return new AgentToolDefinition(name, description, AgentToolSideEffect.READ, "LOW", false, true, inputSchema);
    }

    private static Map<String, Object> pageSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "keyword", Map.of("type", "string"),
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100)
                ),
                "additionalProperties", false
        );
    }
}
