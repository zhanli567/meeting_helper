package com.company.meetinghelper.agent.tool.query;

import java.util.Map;
import org.springframework.stereotype.Component;

/** 将工具调用参数适配为工作区只读查询服务调用。 */
@Component
public class AgentQueryTools {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private final AgentWorkspaceQueryService queryService;

    /** 创建查询工具适配器。
     *
     * @param queryService 工作区只读查询服务
     */
    public AgentQueryTools(AgentWorkspaceQueryService queryService) {
        this.queryService = queryService;
    }

    /** 查询工作区摘要。
     *
     * @param meetingId 运行时会议ID
     * @param arguments 模型参数
     * @return 工作区摘要
     */
    public Object getSummary(String meetingId, Map<String, Object> arguments) {
        return queryService.summarize(meetingId);
    }

    /** 查询未分配参会人。
     *
     * @param meetingId 运行时会议ID
     * @param arguments 模型参数
     * @return 分页查询结果
     */
    public Object listUnassigned(String meetingId, Map<String, Object> arguments) {
        return queryService.listUnassigned(meetingId, keyword(arguments), limit(arguments));
    }

    /** 查询参会人。
     *
     * @param meetingId 运行时会议ID
     * @param arguments 模型参数
     * @return 分页查询结果
     */
    public Object searchParticipants(String meetingId, Map<String, Object> arguments) {
        return queryService.searchParticipants(meetingId, keyword(arguments), limit(arguments));
    }

    /** 查询座位。
     *
     * @param meetingId 运行时会议ID
     * @param arguments 模型参数
     * @return 分页查询结果
     */
    public Object searchSeats(String meetingId, Map<String, Object> arguments) {
        return queryService.searchSeats(meetingId, keyword(arguments), limit(arguments));
    }

    private String keyword(Map<String, Object> arguments) {
        Object value = arguments.get("keyword");
        return value instanceof String ? (String) value : null;
    }

    private int limit(Map<String, Object> arguments) {
        Object value = arguments.get("limit");
        if (!(value instanceof Number)) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, ((Number) value).intValue()));
    }
}
