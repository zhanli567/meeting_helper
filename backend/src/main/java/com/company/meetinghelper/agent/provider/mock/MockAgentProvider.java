package com.company.meetinghelper.agent.provider.mock;

import com.company.meetinghelper.agent.provider.AgentProvider;
import com.company.meetinghelper.agent.provider.AgentProviderRequest;
import com.company.meetinghelper.agent.provider.AgentProviderResponse;
import com.company.meetinghelper.agent.tool.AgentToolCall;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 不访问网络、用于本地开发和测试的规则型 provider。 */
@Component
public class MockAgentProvider implements AgentProvider {
    private static final Pattern SEAT_LABEL_PATTERN = Pattern.compile(".*[A-Za-z][0-9]+.*");
    private final AtomicInteger callSequence = new AtomicInteger();

    /** 根据用户消息选择一个只读工具，或生成中文回答。 *
     * @param request provider 请求
     * @return mock 响应
     */
    @Override
    public AgentProviderResponse next(AgentProviderRequest request) {
        if (request.toolResults().stream().anyMatch(result -> result.success())) {
            return AgentProviderResponse.text("已根据当前工作区数据完成查询。");
        }
        String message = request.chatRequest().message() == null ? "" : request.chatRequest().message();
        String toolName = chooseTool(message);
        if (toolName == null) {
            return AgentProviderResponse.text("我可以先帮你查询未排人员、人员位置、座位占用和会议概况。");
        }
        return AgentProviderResponse.toolCall(new AgentToolCall(
                "mock-call-" + callSequence.incrementAndGet(), toolName, Map.of()));
    }

    private String chooseTool(String message) {
        if (message.contains("未排") || message.contains("没排") || message.contains("没有座位")) {
            return "assignment.list_unassigned";
        }
        if (SEAT_LABEL_PATTERN.matcher(message).matches()) {
            return "seat.search";
        }
        if (message.contains("概况") || message.contains("座位")) {
            return "workspace.get_summary";
        }
        if (message.contains("张") || message.contains("李") || message.contains("王") || message.contains("谁")) {
            return "participant.search";
        }
        return null;
    }
}
