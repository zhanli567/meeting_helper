package com.company.meetinghelper.agent.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 记录智能体运行的最小审计信息。 */
@Component
public class AgentTraceLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentTraceLogger.class);

    /** 记录一次智能体事件。 *
     * @param event 事件
     */
    public void log(AgentEvent event) {
        LOGGER.debug("agent event runId={}, step={}, type={}", event.runId(), event.stepNo(), event.type());
    }
}
