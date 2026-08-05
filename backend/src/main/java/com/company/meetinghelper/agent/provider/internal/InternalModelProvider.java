package com.company.meetinghelper.agent.provider.internal;

import com.company.meetinghelper.agent.provider.AgentProvider;
import com.company.meetinghelper.agent.provider.AgentProviderRequest;
import com.company.meetinghelper.agent.provider.AgentProviderResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 调用内部模型平台的 provider。 */
@Component("internalModelProvider")
public class InternalModelProvider implements AgentProvider {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Environment environment;
    private final HttpClient httpClient;
    private final Function<String, String> tokenResolver;

    /** 内部模型 HTTP 客户端抽象。 */
    public interface HttpClient {
        /** 发送内部模型请求。 */
        String post(String url, String token, Map<String, Object> body);
    }

    private static final class RestClientHttpClient implements HttpClient {
        private final RestClient client = RestClient.builder().build();

        @Override
        public String post(String url, String token, Map<String, Object> body) {
            return client.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token).body(body)
                    .retrieve().body(String.class);
        }
    }

    /** 创建内部模型 provider。 */
    @Autowired
    public InternalModelProvider(Environment environment) {
        this(environment, new RestClientHttpClient());
    }

    /** 创建可注入 HTTP 客户端的 provider。 */
    public InternalModelProvider(Environment environment, HttpClient httpClient) {
        this(environment, httpClient, System::getenv);
    }

    /** 创建可注入 HTTP 客户端与 token 读取器的 provider。 */
    public InternalModelProvider(Environment environment, HttpClient httpClient, Function<String, String> tokenResolver) {
        this.environment = environment;
        this.httpClient = httpClient;
        this.tokenResolver = tokenResolver;
    }

    @Override
    public AgentProviderResponse next(AgentProviderRequest request) {
        String tokenEnv = value("agent.internal.iam-token-env", "MEETING_AGENT_INTERNAL_IAM_TOKEN");
        String token = tokenResolver.apply(tokenEnv);
        if (token == null || token.isBlank()) {
            return AgentProviderResponse.error("INTERNAL_IAM_TOKEN_MISSING", "内部模型 IAM token 未配置");
        }
        String url = value("agent.internal.model-url", "");
        if (url.isBlank()) {
            return AgentProviderResponse.error("INTERNAL_MODEL_URL_MISSING", "内部模型 URL 未配置");
        }
        try {
            JsonNode response = OBJECT_MAPPER.readTree(httpClient.post(url, token, body(request)));
            JsonNode content = response.path("choices").path(0).path("message").path("content");
            return content.isTextual()
                    ? AgentProviderResponse.text(content.asText())
                    : AgentProviderResponse.error("INTERNAL_RESPONSE_INVALID", "内部模型响应缺少 assistant 内容");
        } catch (RestClientException | java.io.IOException exception) {
            return AgentProviderResponse.error("INTERNAL_MODEL_ERROR", "内部模型调用失败");
        }
    }

    private Map<String, Object> body(AgentProviderRequest request) {
        return Map.of("model", value("agent.internal.model", ""),
                "messages", List.of(Map.of("role", "user", "content", request.chatRequest().message())),
                "stream", false);
    }

    private String value(String key, String fallback) {
        String configured = environment.getProperty(key);
        return configured == null || configured.isBlank() ? fallback : configured;
    }
}
