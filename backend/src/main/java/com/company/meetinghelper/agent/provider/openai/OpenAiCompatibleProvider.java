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
import java.util.Set;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

/** 调用外部 OpenAI-compatible Chat Completions API 的 provider。 */
@Component
public class OpenAiCompatibleProvider implements AgentProvider {
    private static final String DEFAULT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "deepseek-chat";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> RESERVED_BODY_KEYS = Set.of(
            "model", "messages", "stream", "tools", "tool_choice");
    private static final Set<String> RESERVED_ASSISTANT_CONTEXT_KEYS = Set.of("role", "tool_calls");
    private static final Map<String, String> EXTERNAL_TOOL_NAMES = Map.of(
            "workspace.get_summary", "workspace_get_summary",
            "assignment.list_unassigned", "assignment_list_unassigned",
            "participant.search", "participant_search",
            "seat.search", "seat_search"
    );

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
        String apiKey = apiKey();
        if (apiKey == null) {
            return AgentProviderResponse.error("EXTERNAL_API_KEY_MISSING",
                    "外部模型 API Key 未配置：请在 application.yml 中配置 agent.external.api-key。");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", value("agent.external.model", DEFAULT_MODEL));
        body.put("messages", messages(request));
        body.put("stream", booleanValue("agent.external.stream", false));
        putExtraParams(body);
        if (!request.toolDefinitions().isEmpty()) {
            body.put("tools", tools(request.toolDefinitions()));
            body.put("tool_choice", value("agent.external.tool-choice", "auto"));
        }
        try {
            String response = httpClient.post(value("agent.external.base-url", DEFAULT_URL), apiKey, body);
            return normalizeToolCallName(parser.parse(response == null ? "" : response));
        } catch (RestClientResponseException exception) {
            return AgentProviderResponse.error("EXTERNAL_PROVIDER_ERROR", externalErrorMessage(exception));
        } catch (RestClientException exception) {
            return AgentProviderResponse.error("EXTERNAL_PROVIDER_ERROR",
                    "外部模型调用失败：" + safeExceptionMessage(exception.getMessage()));
        }
    }

    private String apiKey() {
        String directKey = value("agent.external.api-key", "");
        return directKey.isBlank() ? null : directKey;
    }

    private String value(String key, String fallback) {
        String configured = environment.getProperty(key);
        return configured == null || configured.isBlank() ? fallback : configured;
    }

    private boolean booleanValue(String key, boolean fallback) {
        String configured = value(key, "");
        return configured.isBlank() ? fallback : Boolean.parseBoolean(configured);
    }

    private void putExtraParams(Map<String, Object> body) {
        Map<String, Object> extraParams = Binder.get(environment)
                .bind("agent.external.extra-params", Bindable.mapOf(String.class, Object.class))
                .orElse(Map.of());
        extraParams.forEach((key, value) -> {
            if (!RESERVED_BODY_KEYS.contains(key)) {
                body.put(key, value);
            }
        });
    }

    private String externalErrorMessage(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        String detail = externalErrorDetail(body);
        String status = String.valueOf(exception.getStatusCode().value());
        return detail == null
                ? "外部模型调用失败（HTTP " + status + "）"
                : "外部模型调用失败（HTTP " + status + "）：" + detail;
    }

    private String externalErrorDetail(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = OBJECT_MAPPER.readTree(body);
            com.fasterxml.jackson.databind.JsonNode message = root.path("error").path("message");
            if (message.isMissingNode() || message.asText("").isBlank()) {
                message = root.path("message");
            }
            String text = message.asText("");
            return text.isBlank() ? safeExceptionMessage(body) : safeExceptionMessage(text);
        } catch (Exception ignored) {
            return safeExceptionMessage(body);
        }
    }

    private String safeExceptionMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 220 ? normalized : normalized.substring(0, 220) + "...";
    }

    private List<Map<String, Object>> messages(AgentProviderRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        String systemPrompt = value("agent.external.system-prompt", "");
        if (!systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
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
        function.put("name", externalToolName(call.name()));
        function.put("arguments", argumentsJson(call.arguments()));
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", call.id());
        toolCall.put("type", "function");
        toolCall.put("function", function);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        call.providerContext().forEach((key, value) -> {
            if (!RESERVED_ASSISTANT_CONTEXT_KEYS.contains(key)) {
                message.put(key, value);
            }
        });
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
            function.put("name", externalToolName(definition.name()));
            function.put("description", definition.description());
            function.put("parameters", definition.inputSchema());
            return Map.of("type", "function", "function", function);
        }).toList();
    }

    private AgentProviderResponse normalizeToolCallName(AgentProviderResponse response) {
        AgentToolCall call = response.toolCall();
        if (call == null) {
            return response;
        }
        return AgentProviderResponse.toolCall(new AgentToolCall(
                call.id(), internalToolName(call.name()), call.arguments(), call.providerContext()));
    }

    private String externalToolName(String internalName) {
        return EXTERNAL_TOOL_NAMES.getOrDefault(internalName, internalName.replace('.', '_'));
    }

    private String internalToolName(String externalName) {
        return EXTERNAL_TOOL_NAMES.entrySet().stream()
                .filter(entry -> entry.getValue().equals(externalName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(externalName);
    }
}
