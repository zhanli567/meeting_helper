package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.company.meetinghelper.agent.tool.AgentToolCall;
import com.company.meetinghelper.agent.tool.AgentToolDefinition;
import com.company.meetinghelper.agent.tool.AgentToolExecutor;
import com.company.meetinghelper.agent.tool.AgentToolRegistry;
import com.company.meetinghelper.agent.tool.AgentToolResult;
import com.company.meetinghelper.agent.tool.AgentToolSideEffect;
import com.company.meetinghelper.agent.tool.query.AgentQueryTools;
import com.company.meetinghelper.agent.tool.query.AgentWorkspaceQueryService;
import com.company.meetinghelper.agent.tool.query.WorkspaceSummaryResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentToolExecutionTests {

    @Test
    void registryOnlyExposesReadTools() {
        AgentToolRegistry registry = new AgentToolRegistry();

        assertThat(registry.enabledDefinitions())
                .extracting("name")
                .containsExactlyInAnyOrder(
                        "workspace.get_summary",
                        "assignment.list_unassigned",
                        "participant.search",
                        "seat.search");
        assertThat(registry.enabledDefinitions())
                .allMatch(definition -> "READ".equals(definition.sideEffect().name()));
        assertThat(registry.enabledDefinitions())
                .allMatch(definition -> definition.description().contains("只读"));
        assertThat(registry.enabledDefinitions())
                .allMatch(definition -> definition.description().contains("不改变排座结果"));
    }

    @Test
    void executorRunsSummaryTool() {
        AgentWorkspaceQueryService queryService = mock(AgentWorkspaceQueryService.class);
        when(queryService.summarize("meeting-1"))
                .thenReturn(new WorkspaceSummaryResult("meeting-1", "经营会", 2, 2, 3, 1, 1, 0, 2));
        AgentToolExecutor executor = new AgentToolExecutor(new AgentToolRegistry(), new AgentQueryTools(queryService));

        AgentToolResult result = executor.execute("meeting-1", new AgentToolCall("call-1", "workspace.get_summary", Map.of()));

        assertThat(result.success()).isTrue();
        assertThat(result.toolName()).isEqualTo("workspace.get_summary");
        assertThat(result.data()).isEqualTo(new WorkspaceSummaryResult("meeting-1", "经营会", 2, 2, 3, 1, 1, 0, 2));
        verify(queryService).summarize("meeting-1");
    }

    @Test
    void executorRejectsUnknownToolName() {
        AgentToolExecutor executor = new AgentToolExecutor(
                new AgentToolRegistry(), new AgentQueryTools(mock(AgentWorkspaceQueryService.class)));

        AgentToolResult result = executor.execute("meeting-1", new AgentToolCall("call-1", "meeting.delete", Map.of()));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("TOOL_NOT_ALLOWED");
    }

    @Test
    void executorUsesRuntimeMeetingIdAndBoundsLimit() {
        AgentWorkspaceQueryService queryService = mock(AgentWorkspaceQueryService.class);
        AgentToolExecutor executor = new AgentToolExecutor(new AgentToolRegistry(), new AgentQueryTools(queryService));

        executor.execute("runtime-meeting", new AgentToolCall(
                "call-1", "participant.search", Map.of("meetingId", "model-meeting", "limit", 999)));

        verify(queryService).searchParticipants("runtime-meeting", null, 100);
    }

    @Test
    void participantSearchUsesDefaultLimitOfFifty() {
        AgentWorkspaceQueryService queryService = mock(AgentWorkspaceQueryService.class);
        AgentToolExecutor executor = new AgentToolExecutor(new AgentToolRegistry(), new AgentQueryTools(queryService));

        AgentToolResult result = executor.execute("meeting-1", new AgentToolCall("call-1", "participant.search", Map.of()));

        assertThat(result.success()).isTrue();
        verify(queryService).searchParticipants("meeting-1", null, 50);
    }

    @Test
    void executorRoutesAssignmentListUnassignedTool() {
        AgentWorkspaceQueryService queryService = mock(AgentWorkspaceQueryService.class);
        AgentToolExecutor executor = new AgentToolExecutor(new AgentToolRegistry(), new AgentQueryTools(queryService));

        AgentToolResult result = executor.execute("meeting-1", new AgentToolCall(
                "call-1", "assignment.list_unassigned", Map.of("keyword", "张", "limit", 7)));

        assertThat(result.success()).isTrue();
        verify(queryService).listUnassigned("meeting-1", "张", 7);
    }

    @Test
    void executorRoutesSeatSearchTool() {
        AgentWorkspaceQueryService queryService = mock(AgentWorkspaceQueryService.class);
        AgentToolExecutor executor = new AgentToolExecutor(new AgentToolRegistry(), new AgentQueryTools(queryService));

        AgentToolResult result = executor.execute("meeting-1", new AgentToolCall(
                "call-1", "seat.search", Map.of("keyword", "A1", "limit", 3)));

        assertThat(result.success()).isTrue();
        verify(queryService).searchSeats("meeting-1", "A1", 3);
    }

    @Test
    void executorRejectsKnownNonReadTool() {
        AgentToolRegistry registry = mock(AgentToolRegistry.class);
        when(registry.find("assignment.save"))
                .thenReturn(new AgentToolDefinition(
                        "assignment.save", "写入排座结果", AgentToolSideEffect.COMMIT, "HIGH", true, false, Map.of()));
        AgentWorkspaceQueryService queryService = mock(AgentWorkspaceQueryService.class);
        AgentToolExecutor executor = new AgentToolExecutor(registry, new AgentQueryTools(queryService));

        AgentToolResult result = executor.execute("meeting-1", new AgentToolCall("call-1", "assignment.save", Map.of()));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("TOOL_NOT_ALLOWED");
        verifyNoInteractions(queryService);
    }
}
