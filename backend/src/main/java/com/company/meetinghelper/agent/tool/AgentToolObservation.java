package com.company.meetinghelper.agent.tool;

/** 一次真实工具调用及其执行结果的配对观察。
 *
 * @param call provider 发起的工具调用
 * @param result 本地工具执行后的结果
 */
public record AgentToolObservation(
        AgentToolCall call,
        AgentToolResult result
) {
}
