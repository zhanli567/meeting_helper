package com.company.meetinghelper.agent.api;

import com.company.meetinghelper.agent.api.dto.AgentChatRequest;
import com.company.meetinghelper.agent.runtime.AgentEvent;
import com.company.meetinghelper.agent.runtime.AgentEventType;
import com.company.meetinghelper.agent.runtime.AgentRuntime;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 提供智能体聊天的 SSE 接口。 */
@RestController
@RequestMapping("/agent")
public class AgentController {
    private final AgentRuntime agentRuntime;
    private final AgentSseWriter agentSseWriter;

    /**
     * 创建智能体控制器。
     *
     * @param agentRuntime 智能体运行时
     * @param agentSseWriter SSE 事件转换器
     */
    public AgentController(AgentRuntime agentRuntime, AgentSseWriter agentSseWriter) {
        this.agentRuntime = agentRuntime;
        this.agentSseWriter = agentSseWriter;
    }

    /**
     * 启动一次智能体查询并流式返回事件。
     *
     * @param request 聊天请求
     * @return SSE 响应发射器
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AgentChatRequest request) {
        SseEmitter emitter = new SseEmitter();
        CompletableFuture.runAsync(() -> runAgent(request, emitter));
        return emitter;
    }

    private void runAgent(AgentChatRequest request, SseEmitter emitter) {
        try {
            agentRuntime.run(request, event -> sendEvent(emitter, event));
            emitter.complete();
        } catch (Exception exception) {
            sendError(emitter, request, exception);
            emitter.completeWithError(exception);
        }
    }

    private void sendEvent(SseEmitter emitter, AgentEvent event) {
        try {
            emitter.send(agentSseWriter.toSseEvent(event));
        } catch (Exception exception) {
            throw new AgentSseSendException(exception);
        }
    }

    private void sendError(SseEmitter emitter, AgentChatRequest request, Exception exception) {
        try {
            AgentEvent errorEvent = new AgentEvent(
                    UUID.randomUUID().toString(),
                    request == null ? null : request.conversationId(),
                    UUID.randomUUID().toString(),
                    0,
                    AgentEventType.ERROR,
                    Map.of("code", "AGENT_RUNTIME_ERROR", "message", "智能体处理失败"),
                    OffsetDateTime.now());
            emitter.send(agentSseWriter.toSseEvent(errorEvent));
        } catch (Exception ignored) {
            // SSE 连接已不可用时无法再发送错误事件。
        }
    }

    private static final class AgentSseSendException extends RuntimeException {
        private AgentSseSendException(Exception cause) {
            super(cause);
        }
    }
}
