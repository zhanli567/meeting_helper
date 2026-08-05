package com.company.meetinghelper.agent.provider.internal;

import com.company.meetinghelper.agent.provider.AgentProvider;
import com.company.meetinghelper.agent.provider.AgentProviderRequest;
import com.company.meetinghelper.agent.provider.AgentProviderResponse;
import com.company.meetinghelper.agent.tool.AgentToolObservation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 调用内部智能体平台的 provider。 */
@Component("internalAgentProvider")
public class InternalAgentProvider implements AgentProvider {
    private final Environment environment;
    private final HttpClient httpClient;
    private final InternalAgentSseParser parser;

    /** 内部智能体 HTTP 客户端抽象。 */
    public interface HttpClient {
        /** 发送内部智能体请求。 */
        String post(String url, Map<String, String> headers, Map<String, Object> body);
    }

    private static final class RestClientHttpClient implements HttpClient {
        private final RestClient client = RestClient.builder().build();

        @Override
        public String post(String url, Map<String, String> headers, Map<String, Object> body) {
            return client.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                    .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                    .body(body).retrieve().body(String.class);
        }
    }

    /** 创建内部智能体 provider。 */
    @Autowired
    public InternalAgentProvider(Environment environment) {
        this(environment, new RestClientHttpClient());
    }

    /** 创建可注入 HTTP 客户端的 provider。 */
    public InternalAgentProvider(Environment environment, HttpClient httpClient) {
        this.environment = environment;
        this.httpClient = httpClient;
        this.parser = new InternalAgentSseParser();
    }

    @Override
    public AgentProviderResponse next(AgentProviderRequest request) {
        String url = value("agent.internal.agent-chat-url", "");
        if (url.isBlank()) {
            return AgentProviderResponse.error("INTERNAL_AGENT_URL_MISSING", "内部智能体 URL 未配置");
        }
        try {
            String response = httpClient.post(url, headers(), body(request));
            List<AgentProviderResponse> responses = parser.parse(response == null ? "" : response);
            return normalize(responses);
        } catch (RestClientException exception) {
            return AgentProviderResponse.error("INTERNAL_AGENT_ERROR", "内部智能体调用失败");
        }
    }

    private AgentProviderResponse normalize(List<AgentProviderResponse> responses) {
        if (responses.isEmpty()) {
            return AgentProviderResponse.error("INTERNAL_RESPONSE_INVALID", "内部智能体响应为空");
        }
        AgentProviderResponse error = firstError(responses);
        if (error != null) {
            return error;
        }
        AgentProviderResponse toolCall = firstToolCall(responses);
        if (toolCall != null) {
            return toolCall;
        }
        String text = combinedText(responses);
        if (!text.isBlank()) {
            return AgentProviderResponse.text(text);
        }
        return responses.stream().anyMatch(AgentProviderResponse::done)
                ? new AgentProviderResponse(null, null, true, null, null)
                : AgentProviderResponse.error("INTERNAL_RESPONSE_INVALID", "内部智能体响应缺少可处理内容");
    }

    private AgentProviderResponse firstError(List<AgentProviderResponse> responses) {
        return responses.stream()
                .filter(response -> response.errorCode() != null)
                .findFirst()
                .orElse(null);
    }

    private AgentProviderResponse firstToolCall(List<AgentProviderResponse> responses) {
        return responses.stream()
                .filter(response -> response.toolCall() != null)
                .findFirst()
                .orElse(null);
    }

    private String combinedText(List<AgentProviderResponse> responses) {
        StringJoiner joiner = new StringJoiner("");
        responses.stream()
                .map(AgentProviderResponse::assistantText)
                .filter(text -> text != null && !text.isBlank())
                .forEach(joiner::add);
        return joiner.toString();
    }

    private Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        putIfPresent(headers, "x-space-id", value("agent.internal.space-id", ""));
        putIfPresent(headers, "x-super-agent-id", value("agent.internal.super-agent-id", ""));
        putIfPresent(headers, "x-bundle-id", value("agent.internal.bundle-id", ""));
        putIfPresent(headers, "x-agent-alias", value("agent.internal.agent-alias", ""));
        return headers;
    }

    private void putIfPresent(Map<String, String> headers, String key, String value) {
        if (!value.isBlank()) {
            headers.put(key, value);
        }
    }

    private Map<String, Object> body(AgentProviderRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conversationId", request.chatRequest().conversationId());
        body.put("messages", messages(request));
        body.put("stream", true);
        return body;
    }

    private List<Map<String, Object>> messages(AgentProviderRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", request.chatRequest().message()));
        for (AgentToolObservation observation : request.toolObservations()) {
            messages.add(Map.of("role", "tool", "content", String.valueOf(observation.result().data()),
                    "tool_call_id", observation.call().id()));
        }
        return messages;
    }

    private String value(String key, String fallback) {
        String configured = environment.getProperty(key);
        return configured == null || configured.isBlank() ? fallback : configured;
    }
}
