package com.company.meetinghelper.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 智能体运行配置。
 */
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private boolean enabled = false;
    private String provider = "mock";
    private int maxToolSteps = 8;
    private int maxModelRetriesPerStep = 1;
    private int maxToolRetriesPerStep = 1;
    private int maxDurationSeconds = 60;
    private boolean allowFrontendDraftTools = false;
    private boolean allowCommitTools = false;

    /**
     * 返回智能体是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置智能体是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回智能体 provider 名称。
     *
     * @return provider 名称
     */
    public String getProvider() {
        return provider;
    }

    /**
     * 设置智能体 provider 名称。
     *
     * @param provider provider 名称
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * 返回单次运行允许的工具步骤数。
     *
     * @return 工具步骤数
     */
    public int getMaxToolSteps() {
        return maxToolSteps;
    }

    /**
     * 设置单次运行允许的工具步骤数。
     *
     * @param maxToolSteps 工具步骤数
     */
    public void setMaxToolSteps(int maxToolSteps) {
        this.maxToolSteps = maxToolSteps;
    }

    /**
     * 返回每步模型最大重试次数。
     *
     * @return 模型重试次数
     */
    public int getMaxModelRetriesPerStep() {
        return maxModelRetriesPerStep;
    }

    /**
     * 设置每步模型最大重试次数。
     *
     * @param maxModelRetriesPerStep 模型重试次数
     */
    public void setMaxModelRetriesPerStep(int maxModelRetriesPerStep) {
        this.maxModelRetriesPerStep = maxModelRetriesPerStep;
    }

    /**
     * 返回每步工具最大重试次数。
     *
     * @return 工具重试次数
     */
    public int getMaxToolRetriesPerStep() {
        return maxToolRetriesPerStep;
    }

    /**
     * 设置每步工具最大重试次数。
     *
     * @param maxToolRetriesPerStep 工具重试次数
     */
    public void setMaxToolRetriesPerStep(int maxToolRetriesPerStep) {
        this.maxToolRetriesPerStep = maxToolRetriesPerStep;
    }

    /**
     * 返回单次运行最大持续秒数。
     *
     * @return 持续秒数
     */
    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    /**
     * 设置单次运行最大持续秒数。
     *
     * @param maxDurationSeconds 持续秒数
     */
    public void setMaxDurationSeconds(int maxDurationSeconds) {
        this.maxDurationSeconds = maxDurationSeconds;
    }

    /**
     * 返回是否允许前端草稿工具。
     *
     * @return 是否允许
     */
    public boolean isAllowFrontendDraftTools() {
        return allowFrontendDraftTools;
    }

    /**
     * 设置是否允许前端草稿工具。
     *
     * @param allowFrontendDraftTools 是否允许
     */
    public void setAllowFrontendDraftTools(boolean allowFrontendDraftTools) {
        this.allowFrontendDraftTools = allowFrontendDraftTools;
    }

    /**
     * 返回是否允许提交工具。
     *
     * @return 是否允许
     */
    public boolean isAllowCommitTools() {
        return allowCommitTools;
    }

    /**
     * 设置是否允许提交工具。
     *
     * @param allowCommitTools 是否允许
     */
    public void setAllowCommitTools(boolean allowCommitTools) {
        this.allowCommitTools = allowCommitTools;
    }
}
