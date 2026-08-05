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
        MockEnvironment environment = new MockEnvironment()
                .withProperty("agent.external.api-key-env", "MEETING_HELPER_KEY_DOES_NOT_EXIST");
        RecordingHttpClient client = new RecordingHttpClient();

        AgentProviderResponse response = new OpenAiCompatibleProvider(environment, client)
                .next(new AgentProviderRequest(chatRequest(), List.of(), List.of()));

        assertThat(response.errorCode()).isEqualTo("EXTERNAL_API_KEY_MISSING");
        assertThat(client.calls).isZero();
    }

    @Test
    void providerSendsOpenAiToolDefinitions() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("agent.external.api-key-env", "PATH");
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
        assertThat(function.get("name")).isEqualTo("workspace.get_summary");
        assertThat(function.get("parameters")).isEqualTo(Map.of("type", "object"));
    }

    @Test
    void factorySelectsOpenAiAndFallsBackForUnknownProvider() {
        AgentProperties properties = new AgentProperties();
        AgentProvider mock = request -> AgentProviderResponse.text("mock");
        AgentProvider external = request -> AgentProviderResponse.text("external");
        AgentProviderFactory factory = new AgentProviderFactory(properties, mock, external);

        properties.setProvider("openai-compatible");
        assertThat(factory.current()).isSameAs(external);
        properties.setProvider("unknown");
        assertThat(factory.current()).isSameAs(mock);
    }

    private static AgentChatRequest chatRequest() {
        return new AgentChatRequest("c1", "meeting-1", null, "summary", true, AgentMode.QUERY);
    }

    private static final class RecordingHttpClient implements OpenAiCompatibleProvider.HttpClient {
        private int calls;
        private Map<String, Object> body = Map.of();

        @Override
        public String post(String url, String apiKey, Map<String, Object> requestBody) {
            calls++;
            body = requestBody;
            return "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";
        }
    }
}
