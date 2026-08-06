package com.company.meetinghelper.agent.provider.openai;

import com.company.meetinghelper.agent.provider.AgentProviderResponse;
import com.company.meetinghelper.agent.tool.AgentToolCall;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** 将 OpenAI-compatible JSON 响应归一化为项目 provider 响应。 */
public class OpenAiCompatibleResponseParser {
    private final ObjectMapper objectMapper;

    /** 创建使用默认 Jackson 配置的响应解析器。 */
    public OpenAiCompatibleResponseParser() {
        this(new ObjectMapper());
    }

    /**
     * 创建响应解析器。
     * @param objectMapper JSON 解析器
     */
    public OpenAiCompatibleResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析 OpenAI Chat Completions 风格的响应。
     * @param json 原始 JSON
     * @return 归一化响应
     */
    public AgentProviderResponse parse(String json) {
        try {
            JsonNode message = objectMapper.readTree(json).path("choices").path(0).path("message");
            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                JsonNode call = toolCalls.get(0);
                JsonNode function = call.path("function");
                Map<String, Object> arguments = objectMapper.readValue(
                        function.path("arguments").asText("{}"), new TypeReference<>() { });
                return AgentProviderResponse.toolCall(new AgentToolCall(
                        call.path("id").asText(null), function.path("name").asText(null),
                        arguments, providerContext(message)));
            }
            JsonNode content = message.get("content");
            if (content != null && !content.isNull()) {
                return AgentProviderResponse.text(content.asText());
            }
            return AgentProviderResponse.error("EXTERNAL_RESPONSE_INVALID", "响应缺少 assistant 内容");
        } catch (Exception exception) {
            return AgentProviderResponse.error("EXTERNAL_RESPONSE_INVALID", "无法解析外部模型响应");
        }
    }

    private Map<String, Object> providerContext(JsonNode message) {
        Map<String, Object> context = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = message.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            if ("role".equals(key) || "tool_calls".equals(key)) {
                continue;
            }
            context.put(key, field.getValue().isNull() ? null
                    : objectMapper.convertValue(field.getValue(), Object.class));
        }
        return context;
    }
}
