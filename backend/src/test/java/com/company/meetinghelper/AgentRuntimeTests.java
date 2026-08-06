package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.meetinghelper.agent.api.dto.AgentChatRequest;
import com.company.meetinghelper.agent.api.dto.AgentMode;
import com.company.meetinghelper.agent.config.AgentProperties;
import com.company.meetinghelper.agent.provider.AgentProvider;
import com.company.meetinghelper.agent.provider.AgentProviderFactory;
import com.company.meetinghelper.agent.provider.AgentProviderRequest;
import com.company.meetinghelper.agent.provider.AgentProviderResponse;
import com.company.meetinghelper.agent.provider.openai.OpenAiCompatibleProvider;
import com.company.meetinghelper.agent.runtime.AgentEvent;
import com.company.meetinghelper.agent.runtime.AgentEventType;
import com.company.meetinghelper.agent.runtime.AgentGuardrailService;
import com.company.meetinghelper.agent.runtime.AgentRuntime;
import com.company.meetinghelper.agent.runtime.AgentTraceLogger;
import com.company.meetinghelper.agent.tool.AgentToolCall;
import com.company.meetinghelper.agent.tool.AgentToolExecutor;
import com.company.meetinghelper.agent.tool.AgentToolRegistry;
import com.company.meetinghelper.agent.tool.AgentToolResult;
import com.company.meetinghelper.agent.tool.query.AgentQueryTools;
import com.company.meetinghelper.agent.tool.query.AgentWorkspaceQueryService;
import com.company.meetinghelper.agent.tool.query.WorkspaceSummaryResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class AgentRuntimeTests {

    @Test
    void disabledAgentIsBlockedBeforeAnyToolCall() {
        AgentRuntime runtime = runtime(false, new AgentProviderResponse[0]);

        List<AgentEvent> events = runtime.runOnce(request("当前会议概况", AgentMode.QUERY));

        assertThat(events).extracting(AgentEvent::type)
                .containsExactly(AgentEventType.RUN_STARTED, AgentEventType.GUARDRAIL_BLOCKED, AgentEventType.RUN_DONE);
    }

    @Test
    void nullModeIsBlockedBeforeAnyToolCall() {
        AgentRuntime runtime = runtime(true, new AgentProviderResponse[0]);

        List<AgentEvent> events = runtime.runOnce(request("当前会议概况", null));

        assertThat(events).extracting(AgentEvent::type)
                .containsExactly(AgentEventType.RUN_STARTED, AgentEventType.GUARDRAIL_BLOCKED, AgentEventType.RUN_DONE)
                .doesNotContain(AgentEventType.TOOL_CALL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"保存", "发布", "删除", "恢复版本", "导入", "导出", "落库"})
    void everyWriteIntentIsBlocked(String writeIntent) {
        AgentRuntime runtime = runtime(true, new AgentProviderResponse[0]);

        List<AgentEvent> events = runtime.runOnce(request("请" + writeIntent + "当前排座", AgentMode.QUERY));

        assertThat(events).extracting(AgentEvent::type)
                .containsExactly(AgentEventType.RUN_STARTED, AgentEventType.GUARDRAIL_BLOCKED, AgentEventType.RUN_DONE);
    }

    @Test
    void enabledQueryEmitsEventsInExecutionOrder() {
        AgentRuntime runtime = runtime(true, new AgentProviderResponse[0]);

        List<AgentEvent> events = runtime.runOnce(request("当前会议概况", AgentMode.QUERY));

        assertThat(events).extracting(AgentEvent::type)
                .containsExactly(AgentEventType.RUN_STARTED, AgentEventType.TOOL_CALL,
                        AgentEventType.TOOL_RESULT, AgentEventType.ASSISTANT_TEXT, AgentEventType.RUN_DONE);
    }

    @Test
    void maxToolStepsEmitsErrorAndThenDone() {
        AgentProperties properties = enabledProperties();
        properties.setMaxToolSteps(1);
        AgentProviderResponse toolCall = AgentProviderResponse.toolCall(
                new AgentToolCall("call-1", "workspace.get_summary", Map.of()));
        AgentRuntime runtime = runtime(properties, new RepeatingProvider(toolCall));

        List<AgentEvent> events = runtime.runOnce(request("当前会议概况", AgentMode.QUERY));

        assertThat(events).extracting(AgentEvent::type)
                .containsExactly(AgentEventType.RUN_STARTED, AgentEventType.TOOL_CALL,
                        AgentEventType.TOOL_RESULT, AgentEventType.ERROR, AgentEventType.RUN_DONE);
        assertThat(events.get(3).payload()).containsEntry("code", "MAX_TOOL_STEPS");
    }

    @Test
    void providerErrorEmitsErrorAndThenDone() {
        AgentProviderResponse error = AgentProviderResponse.error("PROVIDER_ERROR", "mock provider failed");
        AgentRuntime runtime = runtime(true, error);

        List<AgentEvent> events = runtime.runOnce(request("当前会议概况", AgentMode.QUERY));

        assertThat(events).extracting(AgentEvent::type)
                .containsExactly(AgentEventType.RUN_STARTED, AgentEventType.ERROR, AgentEventType.RUN_DONE);
        assertThat(events.get(1).payload()).containsEntry("code", "PROVIDER_ERROR");
    }

    @Test
    void longAssistantTextIsSplitIntoSeveralStreamEvents() {
        String text = "以下是当前工作区的摘要信息：会议名称为经营会，参会人员总数为十五人，"
                + "当前已经完成两人的座位分配，还有十三人等待排座。"
                + "如果需要，我可以继续查询未分配人员、空闲座位或指定座位的占用情况。";
        AgentRuntime runtime = runtime(true, AgentProviderResponse.text(text));

        List<AgentEvent> events = runtime.runOnce(request("获取工作区摘要", AgentMode.QUERY));

        List<AgentEvent> textEvents = events.stream()
                .filter(event -> event.type() == AgentEventType.ASSISTANT_TEXT)
                .toList();
        assertThat(textEvents).hasSizeGreaterThan(1);
        assertThat(textEvents.stream()
                .map(event -> String.valueOf(event.payload().get("text")))
                .reduce("", String::concat)).isEqualTo(text);
    }

    @Test
    void unknownToolEmitsErrorWithoutExecutingTool() {
        AgentProviderResponse unknownTool = AgentProviderResponse.toolCall(
                new AgentToolCall("call-1", "assignment.save", Map.of()));
        AgentRuntime runtime = runtime(true, unknownTool);

        List<AgentEvent> events = runtime.runOnce(request("查询", AgentMode.QUERY));

        assertThat(events).extracting(AgentEvent::type)
                .containsExactly(AgentEventType.RUN_STARTED, AgentEventType.ERROR, AgentEventType.RUN_DONE);
        assertThat(events.get(1).payload()).containsEntry("code", "TOOL_NOT_ALLOWED");
    }

    @Test
    void failedToolStillEmitsToolResultThenContinuesToProviderText() {
        AgentToolExecutor executor = mock(AgentToolExecutor.class);
        when(executor.execute("meeting-1", new AgentToolCall("call-1", "workspace.get_summary", Map.of())))
                .thenReturn(AgentToolResult.failure("call-1", "workspace.get_summary", "QUERY_FAILED", "查询失败"));
        AgentProvider provider = new SequenceProvider(
                AgentProviderResponse.toolCall(new AgentToolCall("call-1", "workspace.get_summary", Map.of())),
                AgentProviderResponse.text("查询暂时失败，请稍后重试。"));
        AgentRuntime runtime = runtime(enabledProperties(), provider, executor);

        List<AgentEvent> events = runtime.runOnce(request("当前会议概况", AgentMode.QUERY));

        assertThat(events).extracting(AgentEvent::type)
                .containsExactly(AgentEventType.RUN_STARTED, AgentEventType.TOOL_CALL,
                        AgentEventType.TOOL_RESULT, AgentEventType.ASSISTANT_TEXT, AgentEventType.RUN_DONE);
        assertThat(events.get(2).payload()).containsEntry("success", false);
    }

    @Test
    void externalProviderReceivesAssistantToolCallHistoryBeforeToolResult() {
        AgentProperties properties = enabledProperties();
        properties.setProvider("openai-compatible");
        RecordingOpenAiHttpClient client = new RecordingOpenAiHttpClient(
                "{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"workspace.get_summary\",\"arguments\":\"{\\\"limit\\\":5}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                new MockEnvironment().withProperty("agent.external.api-key", "sk-test"), client);
        AgentRuntime runtime = runtime(properties, provider, realExecutor());

        runtime.runOnce(request("current meeting summary", AgentMode.QUERY));

        assertThat(client.bodies).hasSize(2);
        List<?> messages = (List<?>) client.bodies.get(1).get("messages");
        assertThat(messages).hasSize(3);
        Map<?, ?> assistantMessage = (Map<?, ?>) messages.get(1);
        assertThat(assistantMessage.get("role")).isEqualTo("assistant");
        List<?> toolCalls = (List<?>) assistantMessage.get("tool_calls");
        assertThat(toolCalls).hasSize(1);
        Map<?, ?> toolCall = (Map<?, ?>) toolCalls.get(0);
        assertThat(toolCall.get("id")).isEqualTo("call-1");
        assertThat(toolCall.get("type")).isEqualTo("function");
        Map<?, ?> function = (Map<?, ?>) toolCall.get("function");
        assertThat(function.get("name")).isEqualTo("workspace_get_summary");
        assertThat(function.get("arguments")).isEqualTo("{\"limit\":5}");
        Map<?, ?> toolMessage = (Map<?, ?>) messages.get(2);
        assertThat(toolMessage.get("role")).isEqualTo("tool");
        assertThat(toolMessage.get("tool_call_id")).isEqualTo("call-1");
    }

    private static AgentRuntime runtime(boolean enabled, AgentProviderResponse... responses) {
        return runtime(enabledProperties(enabled), new SequenceProvider(responses), realExecutor());
    }

    private static AgentRuntime runtime(AgentProperties properties, AgentProvider provider) {
        return runtime(properties, provider, realExecutor());
    }

    private static AgentRuntime runtime(AgentProperties properties, AgentProvider provider, AgentToolExecutor executor) {
        AgentToolRegistry registry = new AgentToolRegistry();
        AgentProviderFactory factory = new AgentProviderFactory(properties, provider);
        return new AgentRuntime(properties, registry, executor, factory,
                new AgentGuardrailService(properties), new AgentTraceLogger());
    }

    private static AgentToolExecutor realExecutor() {
        AgentWorkspaceQueryService queryService = mock(AgentWorkspaceQueryService.class);
        when(queryService.summarize("meeting-1"))
                .thenReturn(new WorkspaceSummaryResult("meeting-1", "经营会", 2, 2, 3, 1, 1, 0, 2));
        return new AgentToolExecutor(new AgentToolRegistry(), new AgentQueryTools(queryService));
    }

    private static AgentProperties enabledProperties() {
        return enabledProperties(true);
    }

    private static AgentProperties enabledProperties(boolean enabled) {
        AgentProperties properties = new AgentProperties();
        properties.setEnabled(enabled);
        return properties;
    }

    private static AgentChatRequest request(String message, AgentMode mode) {
        return new AgentChatRequest("c1", "meeting-1", null, message, true, mode);
    }

    private static final class SequenceProvider implements AgentProvider {
        private final List<AgentProviderResponse> responses;
        private int index;

        private SequenceProvider(AgentProviderResponse... responses) {
            this.responses = new ArrayList<>(Arrays.asList(responses));
        }

        @Override
        public AgentProviderResponse next(AgentProviderRequest request) {
            if (responses.isEmpty()) {
                return new com.company.meetinghelper.agent.provider.mock.MockAgentProvider().next(request);
            }
            AgentProviderResponse response = responses.get(Math.min(index, responses.size() - 1));
            index++;
            return response;
        }
    }

    private static final class RepeatingProvider implements AgentProvider {
        private final AgentProviderResponse response;

        private RepeatingProvider(AgentProviderResponse response) {
            this.response = response;
        }

        @Override
        public AgentProviderResponse next(AgentProviderRequest request) {
            return response;
        }
    }

    private static final class RecordingOpenAiHttpClient implements OpenAiCompatibleProvider.HttpClient {
        private final List<String> responses;
        private final List<Map<String, Object>> bodies = new ArrayList<>();
        private int index;

        private RecordingOpenAiHttpClient(String... responses) {
            this.responses = new ArrayList<>(Arrays.asList(responses));
        }

        @Override
        public String post(String url, String apiKey, Map<String, Object> requestBody) {
            bodies.add(requestBody);
            String response = responses.get(Math.min(index, responses.size() - 1));
            index++;
            return response;
        }
    }
}
