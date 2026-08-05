package com.company.meetinghelper.agent.provider;

import com.company.meetinghelper.agent.config.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** 按配置选择当前智能体 provider。 */
@Component
public class AgentProviderFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentProviderFactory.class);
    private final AgentProperties properties;
    private final AgentProvider mockProvider;
    private final AgentProvider openAiCompatibleProvider;
    private final AgentProvider internalAgentProvider;
    private final AgentProvider internalModelProvider;

    /**
     * 创建仅包含 mock provider 的工厂，供现有单元测试使用。
     * @param properties 智能体配置
     * @param mockProvider mock provider
     */
    public AgentProviderFactory(AgentProperties properties, AgentProvider mockProvider) {
        this(properties, mockProvider, mockProvider, mockProvider, mockProvider);
    }

    /** 创建包含 mock 和外部 provider 的兼容工厂。 */
    public AgentProviderFactory(AgentProperties properties,
                                AgentProvider mockProvider,
                                AgentProvider openAiCompatibleProvider) {
        this(properties, mockProvider, openAiCompatibleProvider, mockProvider, mockProvider);
    }

    /**
     * 创建包含外部 provider 的 provider 工厂。
     * @param properties 智能体配置
     * @param mockProvider mock provider
     * @param openAiCompatibleProvider 外部 provider
     */
    @Autowired
    public AgentProviderFactory(AgentProperties properties,
                                @Qualifier("mockAgentProvider") AgentProvider mockProvider,
                                @Qualifier("openAiCompatibleProvider") AgentProvider openAiCompatibleProvider,
                                @Qualifier("internalAgentProvider") AgentProvider internalAgentProvider,
                                @Qualifier("internalModelProvider") AgentProvider internalModelProvider) {
        this.properties = properties;
        this.mockProvider = mockProvider;
        this.openAiCompatibleProvider = openAiCompatibleProvider;
        this.internalAgentProvider = internalAgentProvider;
        this.internalModelProvider = internalModelProvider;
    }

    /**
     * 返回当前配置对应的 provider。
     * @return provider
     */
    public AgentProvider current() {
        if ("openai-compatible".equalsIgnoreCase(properties.getProvider())) {
            return openAiCompatibleProvider;
        }
        if ("internal-agent".equalsIgnoreCase(properties.getProvider())) {
            return internalAgentProvider;
        }
        if ("internal-model".equalsIgnoreCase(properties.getProvider())) {
            return internalModelProvider;
        }
        if (!"mock".equalsIgnoreCase(properties.getProvider())) {
            LOGGER.warn("未知 agent provider '{}'，回退到 mock", properties.getProvider());
        }
        return mockProvider;
    }
}
