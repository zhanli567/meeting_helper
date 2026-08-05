package com.company.meetinghelper.agent.provider.openai;

import com.company.meetinghelper.agent.provider.AgentProvider;
import com.company.meetinghelper.agent.provider.AgentProviderRequest;
import com.company.meetinghelper.agent.provider.AgentProviderResponse;
import com.company.meetinghelper.agent.tool.AgentToolCall;
import com.company.meetinghelper.agent.tool.AgentToolDefinition;
import com.company.meetinghelper.agent.tool.AgentToolObservation;
import com.company.meetinghelper.agent.tool.AgentToolResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

/** 调用外部 OpenAI-compatible Chat Completions API 的 provider。 */
@Component
public class OpenAiCompatibleProvider implements AgentProvider {
    private static final String DEFAULT_API_KEY_ENV = "MEETING_AGENT_EXTERNAL_API_KEY";
    private static final String DEFAULT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "deepseek-chat";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final Environment environment;
    private final OpenAiCompatibleResponseParser parser;

    /** HTTP 客户端边界，便于测试隔离真实网络。 */
    public interface HttpClient {
        /**
         * 发送一次 JSON POST 请求。
         * @param url 请求地址
         * @param apiKey API key
         * @param requestBody 请求体
         * @return 原始响应 JSON
         */
        String post(String url, String apiKey, Map<String, Object> requestBody);
    }

    private static final class RestClientHttpClient implements HttpClient {
        private final RestClient client = RestClient.builder().build();

        @Override
        public String post(String url, String apiKey, Map<String, Object> requestBody) {
            return client.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey).body(requestBody)
                    .retrieve().body(String.class);
        }
    }

    /**
     * 创建外部 provider。
     * @param environment Spring 配置环境
     */
    @Autowired
    public OpenAiCompatibleProvider(Environment environment) {
        this(environment, new RestClientHttpClient(), new OpenAiCompatibleResponseParser());
    }

    /**
     * 创建注入 HTTP 客户端的 provider。
     * @param environment Spring 配置环境
     * @param httpClient HTTP 客户端
     */
    public OpenAiCompatibleProvider(Environment environment, HttpClient httpClient) {
        this(environment, httpClient, new OpenAiCompatibleResponseParser());
    }

    OpenAiCompatibleProvider(Environment environment, HttpClient httpClient,
                             OpenAiCompatibleResponseParser parser) {
        this.environment = environment;
        this.httpClient = httpClient;
        this.parser = parser;
    }

    /**
     * 发起一次非流式外部模型调用。
     * @param request provider 请求
     * @return 归一化 provider 响应
     */
    @Override
    public AgentProviderResponse next(AgentProviderRequest request) {
        String keyEnv = value("agent.external.api-key-env", DEFAULT_API_KEY_ENV);
        String apiKey = System.getenv(keyEnv);
        if (apiKey == null || apiKey.isBlank()) {
            return AgentProviderResponse.error("EXTERNAL_API_KEY_MISSING", "外部模型 API Key 未配置");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", value("agent.external.model", DEFAULT_MODEL));
        body.put("messages", messages(request));
        if (!request.toolDefinitions().isEmpty()) {
            body.put("tools", tools(request.toolDefinitions()));
            body.put("tool_choice", "auto");
        }
        try {
            String response = httpClient.post(value("agent.external.base-url", DEFAULT_URL), apiKey, body);
            return parser.parse(response == null ? "" : response);
        } catch (RestClientException exception) {
            return AgentProviderResponse.error("EXTERNAL_PROVIDER_ERROR", "外部模型调用失败");
        }
    }

    private String value(String key, String fallback) {
        String configured = environment.getProperty(key);
        return configured == null || configured.isBlank() ? fallback : configured;
    }

    private List<Map<String, Object>> messages(AgentProviderRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", request.chatRequest().message()));
        if (!request.toolObservations().isEmpty()) {
            request.toolObservations().forEach(observation -> addObservationMessages(messages, observation));
        } else {
            request.toolResults().forEach(result -> messages.add(toolResultMessage(result.callId(), result)));
        }
        return messages;
    }

    private void addObservationMessages(List<Map<String, Object>> messages, AgentToolObservation observation) {
        messages.add(assistantToolCallMessage(observation.call()));
        messages.add(toolResultMessage(observation.call().id(), observation.result()));
    }

    private Map<String, Object> assistantToolCallMessage(AgentToolCall call) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", call.name());
        function.put("arguments", argumentsJson(call.arguments()));
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", call.id());
        toolCall.put("type", "function");
        toolCall.put("function", function);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("tool_calls", List.of(toolCall));
        return message;
    }

    private Map<String, Object> toolResultMessage(String toolCallId, AgentToolResult result) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("content", String.valueOf(result.data()));
        if (toolCallId != null && !toolCallId.isBlank()) {
            message.put("tool_call_id", toolCallId);
        }
        return message;
    }

    private String argumentsJson(Map<String, Object> arguments) {
        try {
            return OBJECT_MAPPER.writeValueAsString(arguments == null ? Map.of() : arguments);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private List<Map<String, Object>> tools(List<AgentToolDefinition> definitions) {
        return definitions.stream().map(definition -> {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", definition.name());
            function.put("description", definition.description());
            function.put("parameters", definition.inputSchema());
            return Map.of("type", "function", "function", function);
        }).toList();
    }
}
