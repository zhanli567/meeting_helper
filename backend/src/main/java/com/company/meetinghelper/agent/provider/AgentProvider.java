package com.company.meetinghelper.agent.provider;

/** 定义智能体模型提供方的统一调用边界。 */
public interface AgentProvider {

    /** 根据当前请求返回下一步模型动作。 *
     * @param request provider 请求
     * @return provider 响应
     */
    AgentProviderResponse next(AgentProviderRequest request);
}
