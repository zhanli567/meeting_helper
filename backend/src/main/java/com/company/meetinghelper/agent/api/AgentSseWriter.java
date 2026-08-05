package com.company.meetinghelper.agent.api;

import com.company.meetinghelper.agent.runtime.AgentEvent;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 将智能体归一化事件转换为标准 SSE 事件。 */
@Component
public class AgentSseWriter {

    /**
     * 转换智能体事件。
     *
     * @param event 归一化智能体事件
     * @return SSE 事件
     */
    public SseEmitter.SseEventBuilder toSseEvent(AgentEvent event) {
        String eventName = event.type().name().toLowerCase(Locale.ROOT);
        return SseEmitter.event().name(eventName).data(event);
    }
}
