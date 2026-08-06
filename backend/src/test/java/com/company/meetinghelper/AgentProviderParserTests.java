package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.meetinghelper.agent.provider.openai.OpenAiCompatibleResponseParser;
import com.company.meetinghelper.agent.api.dto.AgentChatRequest;
import com.company.meetinghelper.agent.api.dto.AgentMode;
import com.company.meetinghelper.agent.config.AgentProperties;
import com.company.meetinghelper.agent.provider.AgentProvider;
import com.company.meetinghelper.agent.provider.AgentProviderFactory;
import com.company.meetinghelper.agent.provider.AgentProviderRequest;
import com.company.meetinghelper.agent.provider.AgentProviderResponse;
import com.company.meetinghelper.agent.provider.openai.OpenAiCompatibleProvider;
import com.company.meetinghelper.agent.tool.AgentToolDefinition;
import com.company.meetinghelper.agent.tool.AgentToolSideEffect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.Test;

class AgentProviderParserTests {

    @Test
    void parsesToolCall() throws Exception {
        String json = Files.readString(Path.of("src/test/resources/agent/openai-tool-call-response.json"));

        AgentProviderResponse response = new OpenAiCompatibleResponseParser().parse(json);

        assertThat(response.toolCall()).isNotNull();
        assertThat(response.toolCall().name()).isEqualTo("workspace.get_summary");
        assertThat(response.toolCall().arguments()).containsEntry("limit", 5);
    }

    @Test
    void parsesAssistantText() throws Exception {
        String json = Files.readString(Path.of("src/test/resources/agent/openai-text-response.json"));

        AgentProviderResponse response = new OpenAiCompatibleResponseParser().parse(json);

        assertThat(response.assistantText()).contains("当前会议");
        assertThat(response.done()).isTrue();
    }

    @Test
    void providerReturnsMissingKeyWithoutCallingHttpClient() {
        MockEnvironment environment = new MockEnvironment();
        RecordingHttpClient client = new RecordingHttpClient();

        AgentProviderResponse response = new OpenAiCompatibleProvider(environment, client)
                .next(new AgentProviderRequest(chatRequest(), List.of(), List.of()));

        assertThat(response.errorCode()).isEqualTo("EXTERNAL_API_KEY_MISSING");
        assertThat(client.calls).isZero();
    }

    @Test
    void providerSendsOpenAiToolDefinitions() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("agent.external.api-key", "sk-test")
                .withProperty("agent.external.system-prompt", "你是排座查询助手")
                .withProperty("agent.external.tool-choice", "auto")
                .withProperty("agent.external.extra-params.thinking.type", "enabled")
                .withProperty("agent.external.extra-params.reasoning_effort", "high");
        RecordingHttpClient client = new RecordingHttpClient();
        AgentToolDefinition definition = new AgentToolDefinition(
                "workspace.get_summary", "summary", AgentToolSideEffect.READ,
                "LOW", false, true, Map.of("type", "object"));

        new OpenAiCompatibleProvider(environment, client).next(new AgentProviderRequest(
                chatRequest(), List.of(), List.of(definition)));

        assertThat(client.body.get("tool_choice")).isEqualTo("auto");
        assertThat(client.body.get("tools")).asList().first().isInstanceOf(Map.class);
        Map<?, ?> tool = (Map<?, ?>) ((List<?>) client.body.get("tools")).get(0);
        assertThat(tool.get("type")).isEqualTo("function");
        Map<?, ?> function = (Map<?, ?>) tool.get("function");
        assertThat(function.get("name")).isEqualTo("workspace_get_summary");
        assertThat(function.get("parameters")).isEqualTo(Map.of("type", "object"));
        assertThat(client.body).containsEntry("stream", false)
                .containsEntry("reasoning_effort", "high");
        assertThat(client.body.get("thinking")).isEqualTo(Map.of("type", "enabled"));
        assertThat(client.body.get("messages")).asList().first().isEqualTo(
                Map.of("role", "system", "content", "你是排座查询助手"));
    }

    @Test
    void providerMapsExternalToolCallNameBackToInternalName() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("agent.external.api-key", "sk-test");
        RecordingHttpClient client = new RecordingHttpClient();
        client.response = "{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"call-1\","
                + "\"type\":\"function\",\"function\":{\"name\":\"participant_search\","
                + "\"arguments\":\"{\\\"keyword\\\":\\\"张\\\"}\"}}]}}]}";

        AgentProviderResponse response = new OpenAiCompatibleProvider(environment, client)
                .next(new AgentProviderRequest(chatRequest(), List.of(), List.of()));

        assertThat(response.toolCall()).isNotNull();
        assertThat(response.toolCall().name()).isEqualTo("participant.search");
        assertThat(response.toolCall().arguments()).containsEntry("keyword", "张");
    }

    @Test
    void parserKeepsAssistantToolCallProviderContextForFollowupRequest() {
        String json = "{\"choices\":[{\"message\":{\"content\":null,"
                + "\"reasoning_content\":\"先查询当前工作区\","
                + "\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\","
                + "\"function\":{\"name\":\"workspace_get_summary\",\"arguments\":\"{}\"}}]}}]}";

        AgentProviderResponse response = new OpenAiCompatibleResponseParser().parse(json);

        assertThat(response.toolCall()).isNotNull();
        assertThat(response.toolCall().providerContext()).containsEntry("reasoning_content", "先查询当前工作区");
        assertThat(response.toolCall().providerContext()).containsKey("content");
    }

    @Test
    void providerReplaysAssistantToolCallProviderContextInFollowupRequest() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("agent.external.api-key", "sk-test");
        RecordingHttpClient client = new RecordingHttpClient();
        AgentToolCallWithContext call = new AgentToolCallWithContext();
        AgentProviderRequest followup = new AgentProviderRequest(chatRequest(), List.of(),
                List.of(), List.of(call.observation()));

        new OpenAiCompatibleProvider(environment, client).next(followup);

        List<?> messages = (List<?>) client.body.get("messages");
        Map<?, ?> assistantMessage = (Map<?, ?>) messages.get(1);
        assertThat(assistantMessage.get("reasoning_content")).isEqualTo("先查询当前工作区");
        assertThat(assistantMessage.containsKey("content")).isTrue();
    }

    @Test
    void factorySelectsExternalAliasesAndFallsBackForUnknownProvider() {
        AgentProperties properties = new AgentProperties();
        AgentProvider mock = request -> AgentProviderResponse.text("mock");
        AgentProvider external = request -> AgentProviderResponse.text("external");
        AgentProviderFactory factory = new AgentProviderFactory(properties, mock, external);

        properties.setProvider("openai-compatible");
        assertThat(factory.current()).isSameAs(external);
        properties.setProvider("external");
        assertThat(factory.current()).isSameAs(external);
        properties.setProvider("deepseek");
        assertThat(factory.current()).isSameAs(external);
        properties.setProvider("openai");
        assertThat(factory.current()).isSameAs(external);
        properties.setProvider("unknown");
        assertThat(factory.current()).isSameAs(mock);
    }

    private static AgentChatRequest chatRequest() {
        return new AgentChatRequest("c1", "meeting-1", null, "summary", true, AgentMode.QUERY);
    }

    private static final class AgentToolCallWithContext {
        private com.company.meetinghelper.agent.tool.AgentToolObservation observation() {
            Map<String, Object> providerContext = new java.util.LinkedHashMap<>();
            providerContext.put("reasoning_content", "先查询当前工作区");
            providerContext.put("content", null);
            com.company.meetinghelper.agent.tool.AgentToolCall call =
                    new com.company.meetinghelper.agent.tool.AgentToolCall(
                            "call-1", "workspace.get_summary", Map.of(), providerContext);
            com.company.meetinghelper.agent.tool.AgentToolResult result =
                    com.company.meetinghelper.agent.tool.AgentToolResult.success(
                            "call-1", "workspace.get_summary", Map.of("assignedCount", 2));
            return new com.company.meetinghelper.agent.tool.AgentToolObservation(call, result);
        }
    }

    private static final class RecordingHttpClient implements OpenAiCompatibleProvider.HttpClient {
        private int calls;
        private Map<String, Object> body = Map.of();
        private String response = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";

        @Override
        public String post(String url, String apiKey, Map<String, Object> requestBody) {
            calls++;
            body = requestBody;
            return response;
        }
    }
}
