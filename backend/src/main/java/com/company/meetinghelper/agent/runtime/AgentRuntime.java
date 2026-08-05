package com.company.meetinghelper.agent.runtime;

import com.company.meetinghelper.agent.api.dto.AgentChatRequest;
import com.company.meetinghelper.agent.config.AgentProperties;
import com.company.meetinghelper.agent.provider.AgentProviderFactory;
import com.company.meetinghelper.agent.provider.AgentProviderRequest;
import com.company.meetinghelper.agent.provider.AgentProviderResponse;
import com.company.meetinghelper.agent.tool.AgentToolCall;
import com.company.meetinghelper.agent.tool.AgentToolExecutor;
import com.company.meetinghelper.agent.tool.AgentToolObservation;
import com.company.meetinghelper.agent.tool.AgentToolRegistry;
import com.company.meetinghelper.agent.tool.AgentToolResult;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/** 驱动 provider 与只读工具之间交互的智能体运行循环。 */
@Service
public class AgentRuntime {
    private final AgentProperties properties;
    private final AgentToolRegistry registry;
    private final AgentToolExecutor executor;
    private final AgentProviderFactory providerFactory;
    private final AgentGuardrailService guardrails;
    private final AgentTraceLogger traceLogger;

    /** 创建运行时。 *
     * @param properties 智能体配置
     * @param registry 工具注册表
     * @param executor 工具执行器
     * @param providerFactory provider 工厂
     * @param guardrails guardrail 服务
     * @param traceLogger trace 日志器
     */
    public AgentRuntime(AgentProperties properties, AgentToolRegistry registry, AgentToolExecutor executor,
                        AgentProviderFactory providerFactory, AgentGuardrailService guardrails,
                        AgentTraceLogger traceLogger) {
        this.properties = properties;
        this.registry = registry;
        this.executor = executor;
        this.providerFactory = providerFactory;
        this.guardrails = guardrails;
        this.traceLogger = traceLogger;
    }

    /** 运行智能体并将事件按顺序交给调用方。 *
     * @param request 聊天请求
     * @param eventSink 事件接收器
     */
    public void run(AgentChatRequest request, Consumer<AgentEvent> eventSink) {
        String runId = UUID.randomUUID().toString();
        EventEmitter emitter = new EventEmitter(runId, request, eventSink);
        emitter.emit(0, AgentEventType.RUN_STARTED, Map.of("provider", properties.getProvider()));
        String blockedReason = guardrails.blockedReason(request);
        if (blockedReason != null) {
            emitter.emit(0, AgentEventType.GUARDRAIL_BLOCKED, Map.of("reason", blockedReason));
            emitter.emit(0, AgentEventType.RUN_DONE, Map.of());
            return;
        }
        runLoop(request, emitter);
        emitter.emit(emitter.stepNo(), AgentEventType.RUN_DONE, Map.of());
    }

    /** 同步运行一次并收集所有事件，便于测试和本地调用。 *
     * @param request 聊天请求
     * @return 事件列表
     */
    public List<AgentEvent> runOnce(AgentChatRequest request) {
        List<AgentEvent> events = new ArrayList<>();
        run(request, events::add);
        return List.copyOf(events);
    }

    private void runLoop(AgentChatRequest request, EventEmitter emitter) {
        List<AgentToolResult> toolResults = new ArrayList<>();
        List<AgentToolObservation> toolObservations = new ArrayList<>();
        for (int step = 1; step <= properties.getMaxToolSteps(); step++) {
            AgentProviderResponse response = providerFactory.current().next(new AgentProviderRequest(
                    request, List.copyOf(toolResults), registry.enabledDefinitions(), List.copyOf(toolObservations)));
            if (response.errorCode() != null) {
                emitter.emit(step, AgentEventType.ERROR, Map.of("code", response.errorCode(), "message", response.errorMessage()));
                return;
            }
            if (response.assistantText() != null) {
                emitter.emit(step, AgentEventType.ASSISTANT_TEXT, Map.of("text", response.assistantText()));
            }
            AgentToolCall call = response.toolCall();
            if (call == null) {
                return;
            }
            if (registry.find(call.name()) == null) {
                emitter.emit(step, AgentEventType.ERROR, Map.of("code", "TOOL_NOT_ALLOWED", "message", "工具不在只读白名单中"));
                return;
            }
            emitter.emit(step, AgentEventType.TOOL_CALL, Map.of("callId", call.id(), "toolName", call.name(), "arguments", call.arguments()));
            AgentToolResult result = executor.execute(request.meetingId(), call);
            toolResults.add(result);
            toolObservations.add(new AgentToolObservation(call, result));
            emitter.emit(step, AgentEventType.TOOL_RESULT, payloadForToolResult(result));
            if (response.done()) {
                return;
            }
        }
        emitter.emit(emitter.stepNo() + 1, AgentEventType.ERROR, Map.of("code", "MAX_TOOL_STEPS", "message", "已达到最大工具步数"));
    }

    private Map<String, Object> payloadForToolResult(AgentToolResult result) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("callId", result.callId());
        payload.put("toolName", result.toolName());
        payload.put("success", result.success());
        payload.put("data", result.data());
        if (result.errorCode() != null) {
            payload.put("errorCode", result.errorCode());
        }
        if (result.message() != null) {
            payload.put("message", result.message());
        }
        return payload;
    }

    private final class EventEmitter {
        private final String runId;
        private final AgentChatRequest request;
        private final Consumer<AgentEvent> sink;
        private int stepNo;

        private EventEmitter(String runId, AgentChatRequest request, Consumer<AgentEvent> sink) {
            this.runId = runId;
            this.request = request;
            this.sink = sink;
        }

        private int stepNo() {
            return stepNo;
        }

        private void emit(int step, AgentEventType type, Map<String, Object> payload) {
            stepNo = Math.max(stepNo, step);
            AgentEvent event = new AgentEvent(runId, request == null ? null : request.conversationId(),
                    UUID.randomUUID().toString(), step, type, payload, OffsetDateTime.now());
            traceLogger.log(event);
            sink.accept(event);
        }
    }
}
