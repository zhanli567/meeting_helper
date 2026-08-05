package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.meetinghelper.agent.api.dto.AgentChatRequest;
import com.company.meetinghelper.agent.api.dto.AgentMode;
import com.company.meetinghelper.agent.config.AgentProperties;
import com.company.meetinghelper.agent.provider.AgentProvider;
import com.company.meetinghelper.agent.provider.AgentProviderFactory;
import com.company.meetinghelper.agent.provider.AgentProviderRequest;
import com.company.meetinghelper.agent.provider.AgentProviderResponse;
import com.company.meetinghelper.agent.provider.internal.InternalAgentProvider;
import com.company.meetinghelper.agent.provider.internal.InternalAgentSseParser;
import com.company.meetinghelper.agent.provider.internal.InternalModelProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class InternalAgentProviderParserTests {

    @Test
    void parsesToolCallFromDeltaContent() throws Exception {
        String sse = Files.readString(Path.of("src/test/resources/agent/internal-agent-tool-call.sse"));

        List<AgentProviderResponse> responses = new InternalAgentSseParser().parse(sse);

        assertThat(responses).anyMatch(response ->
                response.toolCall() != null
                        && "workspace.get_summary".equals(response.toolCall().name()));
    }

    @Test
    void parsesTextAndDone() throws Exception {
        String sse = Files.readString(Path.of("src/test/resources/agent/internal-agent-text.sse"));

        List<AgentProviderResponse> responses = new InternalAgentSseParser().parse(sse);

        assertThat(responses).anyMatch(response -> response.assistantText().contains("当前会议"));
        assertThat(responses).anyMatch(AgentProviderResponse::done);
    }

    @Test
    void parsesToolResponseAsObservationTextAndErrors() {
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":["
                + "{\"type\":\"tool_response\",\"text\":\"工具返回 10 人\"},"
                + "{\"type\":\"reasoning\",\"text\":\"内部推理\"},"
                + "{\"type\":\"error\",\"message\":\"调用失败\"}]}}]}\n\n"
                + "data: [DONE]\n";

        List<AgentProviderResponse> responses = new InternalAgentSseParser().parse(sse);

        assertThat(responses).anyMatch(response -> response.assistantText().contains("工具返回 10 人"));
        assertThat(responses).anyMatch(response -> response.errorCode() != null);
        assertThat(responses).anyMatch(AgentProviderResponse::done);
    }

    @Test
    void ignoresUnknownContentTypesSafely() {
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":["
                + "{\"type\":\"unknown_internal_card\",\"payload\":{\"title\":\"忽略我\"}}]}}]}\n\n"
                + "data: [DONE]\n";

        List<AgentProviderResponse> responses = new InternalAgentSseParser().parse(sse);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).done()).isTrue();
    }

    @Test
    void internalAgentKeepsToolCallWhenTextAppearsBeforeToolCall() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("agent.internal.agent-chat-url", "http://fake.internal/agent");
        RecordingAgentHttpClient client = new RecordingAgentHttpClient();
        client.response = "data: {\"choices\":[{\"delta\":{\"content\":["
                + "{\"type\":\"text\",\"text\":\"我先看一下会议\"},"
                + "{\"type\":\"tool_call\",\"id\":\"call-1\",\"name\":\"workspace.get_summary\","
                + "\"arguments\":\"{\\\"includeSeats\\\":true}\"}]}}]}\n\n"
                + "data: [DONE]\n";

        AgentProviderResponse response = new InternalAgentProvider(environment, client)
                .next(new AgentProviderRequest(chatRequest()));

        assertThat(response.toolCall()).isNotNull();
        assertThat(response.toolCall().name()).isEqualTo("workspace.get_summary");
        assertThat(response.toolCall().arguments()).containsEntry("includeSeats", true);
        assertThat(response.assistantText()).isNull();
    }

    @Test
    void internalAgentCombinesTextChunksWhenNoToolCallExists() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("agent.internal.agent-chat-url", "http://fake.internal/agent");
        RecordingAgentHttpClient client = new RecordingAgentHttpClient();
        client.response = "data: {\"choices\":[{\"delta\":{\"content\":["
                + "{\"type\":\"text\",\"text\":\"当前会议\"},"
                + "{\"type\":\"text\",\"text\":\"共有 10 人\"}]}}]}\n\n"
                + "data: [DONE]\n";

        AgentProviderResponse response = new InternalAgentProvider(environment, client)
                .next(new AgentProviderRequest(chatRequest()));

        assertThat(response.assistantText()).isEqualTo("当前会议共有 10 人");
        assertThat(response.done()).isTrue();
    }

    @Test
    void internalAgentSendsConfiguredHeadersAndBody() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("agent.internal.agent-chat-url", "http://fake.internal/agent")
                .withProperty("agent.internal.space-id", "space-1")
                .withProperty("agent.internal.super-agent-id", "super-1")
                .withProperty("agent.internal.bundle-id", "bundle-1")
                .withProperty("agent.internal.agent-alias", "assistant");
        RecordingAgentHttpClient client = new RecordingAgentHttpClient();

        new InternalAgentProvider(environment, client).next(new AgentProviderRequest(chatRequest()));

        assertThat(client.url).isEqualTo("http://fake.internal/agent");
        assertThat(client.headers).containsEntry("x-space-id", "space-1")
                .containsEntry("x-super-agent-id", "super-1")
                .containsEntry("x-bundle-id", "bundle-1")
                .containsEntry("x-agent-alias", "assistant");
        assertThat(client.body).containsEntry("conversationId", "c1")
                .containsEntry("stream", true)
                .containsKey("messages");
    }

    @Test
    void internalAgentReturnsMissingUrlWithoutCallingHttpClient() {
        RecordingAgentHttpClient client = new RecordingAgentHttpClient();

        AgentProviderResponse response = new InternalAgentProvider(new MockEnvironment(), client)
                .next(new AgentProviderRequest(chatRequest()));

        assertThat(response.errorCode()).isEqualTo("INTERNAL_AGENT_URL_MISSING");
        assertThat(client.calls).isZero();
    }

    @Test
    void internalModelReturnsMissingTokenWithoutCallingHttpClient() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("agent.internal.iam-token-env", "MEETING_HELPER_TOKEN_DOES_NOT_EXIST");
        RecordingModelHttpClient client = new RecordingModelHttpClient();

        AgentProviderResponse response = new InternalModelProvider(environment, client)
                .next(new AgentProviderRequest(chatRequest()));

        assertThat(response.errorCode()).isEqualTo("INTERNAL_IAM_TOKEN_MISSING");
        assertThat(client.calls).isZero();
    }

    @Test
    void internalModelSendsNonStreamingChatRequestAndParsesContent() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("agent.internal.iam-token-env", "INTERNAL_TEST_TOKEN")
                .withProperty("agent.internal.model-url", "http://fake.internal/model")
                .withProperty("agent.internal.model", "company-chat");
        RecordingModelHttpClient client = new RecordingModelHttpClient();

        AgentProviderResponse response = new InternalModelProvider(environment, client, envName -> "fake-token")
                .next(new AgentProviderRequest(chatRequest()));

        assertThat(response.assistantText()).isEqualTo("ok");
        assertThat(client.calls).isEqualTo(1);
        assertThat(client.url).isEqualTo("http://fake.internal/model");
        assertThat(client.token).isEqualTo("fake-token");
        assertThat(client.body).containsEntry("model", "company-chat")
                .containsEntry("stream", false)
                .containsKey("messages");
    }

    @Test
    void factorySelectsInternalProviders() {
        AgentProperties properties = new AgentProperties();
        AgentProvider mock = request -> AgentProviderResponse.text("mock");
        AgentProvider external = request -> AgentProviderResponse.text("external");
        AgentProvider internalAgent = request -> AgentProviderResponse.text("internal-agent");
        AgentProvider internalModel = request -> AgentProviderResponse.text("internal-model");
        AgentProviderFactory factory = new AgentProviderFactory(
                properties, mock, external, internalAgent, internalModel);

        properties.setProvider("internal-agent");
        assertThat(factory.current()).isSameAs(internalAgent);
        properties.setProvider("internal-model");
        assertThat(factory.current()).isSameAs(internalModel);
    }

    private static AgentChatRequest chatRequest() {
        return new AgentChatRequest("c1", "meeting-1", null, "summary", true, AgentMode.QUERY);
    }

    private static final class RecordingAgentHttpClient implements InternalAgentProvider.HttpClient {
        private int calls;
        private String url;
        private Map<String, String> headers = Map.of();
        private Map<String, Object> body = Map.of();
        private String response = "data: [DONE]\n";

        @Override
        public String post(String requestUrl, Map<String, String> requestHeaders, Map<String, Object> requestBody) {
            calls++;
            url = requestUrl;
            headers = requestHeaders;
            body = requestBody;
            return response;
        }
    }

    private static final class RecordingModelHttpClient implements InternalModelProvider.HttpClient {
        private int calls;
        private String url;
        private String token;
        private Map<String, Object> body = Map.of();

        @Override
        public String post(String url, String token, Map<String, Object> requestBody) {
            calls++;
            this.url = url;
            this.token = token;
            body = requestBody;
            return "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";
        }
    }
}
