package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.meetinghelper.agent.api.dto.AgentChatRequest;
import com.company.meetinghelper.agent.api.dto.AgentMode;
import com.company.meetinghelper.agent.provider.AgentProvider;
import com.company.meetinghelper.agent.provider.AgentProviderRequest;
import com.company.meetinghelper.agent.provider.AgentProviderResponse;
import com.company.meetinghelper.agent.tool.AgentToolResult;
import com.company.meetinghelper.agent.provider.mock.MockAgentProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MockAgentProviderTests {

    @Test
    void mapsUnassignedIntentToUnassignedTool() {
        AgentProviderResponse response = next("当前还有哪些未排人员");
        AgentProviderResponse conversational = next("还有谁没排座");

        assertThat(response.toolCall().name()).isEqualTo("assignment.list_unassigned");
        assertThat(conversational.toolCall().name()).isEqualTo("assignment.list_unassigned");
    }

    @Test
    void mapsSummaryAndSeatIntentsToSummaryTool() {
        AgentProviderResponse summary = next("当前会议概况");
        AgentProviderResponse seat = next("座位占用情况");

        assertThat(summary.toolCall().name()).isEqualTo("workspace.get_summary");
        assertThat(seat.toolCall().name()).isEqualTo("workspace.get_summary");
    }

    @Test
    void mapsSeatLookupIntentToSeatSearchTool() {
        AgentProviderResponse response = next("A1 是谁");

        assertThat(response.toolCall().name()).isEqualTo("seat.search");
    }

    @Test
    void mapsParticipantNameIntentsToParticipantSearchTool() {
        assertThat(next("张三坐在哪里").toolCall().name()).isEqualTo("participant.search");
        assertThat(next("李四坐在哪里").toolCall().name()).isEqualTo("participant.search");
        assertThat(next("王五坐在哪里").toolCall().name()).isEqualTo("participant.search");
    }

    @Test
    void returnsHelpTextForUnmatchedIntent() {
        AgentProviderResponse response = next("你好");

        assertThat(response.assistantText()).isEqualTo("我可以先帮你查询未排人员、人员位置、座位占用和会议概况。");
        assertThat(response.done()).isTrue();
        assertThat(response.toolCall()).isNull();
    }

    @Test
    void returnsChineseSummaryAfterSuccessfulToolResult() {
        AgentProvider provider = new MockAgentProvider();
        AgentProviderRequest request = request("当前会议概况", List.of(
                AgentToolResult.success("call-1", "workspace.get_summary", Map.of("total", 3))));

        AgentProviderResponse response = provider.next(request);

        assertThat(response.assistantText()).isNotBlank();
        assertThat(response.assistantText()).contains("当前工作区数据");
        assertThat(response.done()).isTrue();
        assertThat(response.toolCall()).isNull();
    }

    private static AgentProviderResponse next(String message) {
        return new MockAgentProvider().next(request(message, List.of()));
    }

    private static AgentProviderRequest request(String message, List<AgentToolResult> toolResults) {
        AgentChatRequest chatRequest = new AgentChatRequest("c1", "meeting-1", null, message, true, AgentMode.QUERY);
        return new AgentProviderRequest(chatRequest, toolResults);
    }
}
