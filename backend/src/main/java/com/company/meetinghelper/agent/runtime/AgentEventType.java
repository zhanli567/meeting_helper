package com.company.meetinghelper.agent.runtime;

/**
 * 智能体事件类型。
 */
public enum AgentEventType {
    RUN_STARTED,
    ASSISTANT_TEXT,
    TOOL_CALL,
    TOOL_RESULT,
    GUARDRAIL_BLOCKED,
    ERROR,
    RUN_DONE
}
