package com.company.meetinghelper.agent.provider.internal;

import com.company.meetinghelper.agent.provider.AgentProviderResponse;
import com.company.meetinghelper.agent.tool.AgentToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;

/** 解析内部智能体平台的 SSE 输出。 */
public class InternalAgentSseParser {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> ARGUMENTS_TYPE = new TypeReference<>() { };

    /** 将内部 SSE 文本转换为统一 provider 响应。 *
     * @param sseText SSE 文本
     * @return 归一化响应
     */
    public List<AgentProviderResponse> parse(String sseText) {
        List<AgentProviderResponse> responses = new ArrayList<>();
        if (sseText == null || sseText.isBlank()) {
            return responses;
        }
        for (String line : sseText.split("\\R")) {
            String data = dataOf(line);
            if (data == null) {
                continue;
            }
            if ("[DONE]".equals(data)) {
                responses.add(new AgentProviderResponse(null, null, true, null, null));
                continue;
            }
            parseData(data, responses);
        }
        return responses;
    }

    private String dataOf(String line) {
        if (!line.startsWith("data:")) {
            return null;
        }
        return line.substring(5).trim();
    }

    private void parseData(String data, List<AgentProviderResponse> responses) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(data);
            JsonNode contents = root.path("choices").path(0).path("delta").path("content");
            if (!contents.isArray()) {
                return;
            }
            for (JsonNode content : contents) {
                parseContent(content, responses);
            }
        } catch (Exception ignored) {
            responses.add(AgentProviderResponse.error("INTERNAL_RESPONSE_INVALID", "内部智能体响应无法解析"));
        }
    }

    private void parseContent(JsonNode content, List<AgentProviderResponse> responses) {
        String type = content.path("type").asText("");
        if ("tool_call".equals(type)) {
            responses.add(AgentProviderResponse.toolCall(new AgentToolCall(
                    text(content, "id"), text(content, "name"), arguments(content.path("arguments")))));
            return;
        }
        if ("error".equals(type)) {
            responses.add(AgentProviderResponse.error("INTERNAL_AGENT_ERROR", safeText(content, "message")));
            return;
        }
        String text = safeText(content, "text");
        if (text != null) {
            responses.add(new AgentProviderResponse(text, null, false, null, null));
        }
    }

    private String text(JsonNode node, String field) {
        return node.path(field).isMissingNode() ? null : node.path(field).asText(null);
    }

    private String safeText(JsonNode node, String field) {
        String value = text(node, field);
        if (value != null && !value.isBlank()) {
            return value;
        }
        String content = text(node, "content");
        if (content != null && !content.isBlank()) {
            return content;
        }
        String result = text(node, "result");
        return result == null || result.isBlank() ? null : result;
    }

    private Map<String, Object> arguments(JsonNode node) {
        if (node.isObject()) {
            return OBJECT_MAPPER.convertValue(node, ARGUMENTS_TYPE);
        }
        if (node.isTextual()) {
            try {
                JsonNode parsed = OBJECT_MAPPER.readTree(node.asText());
                return parsed.isObject() ? OBJECT_MAPPER.convertValue(parsed, ARGUMENTS_TYPE) : Map.of();
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }
}
