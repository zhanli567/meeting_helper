package com.company.meetinghelper.agent.runtime;

import com.company.meetinghelper.agent.api.dto.AgentChatRequest;
import com.company.meetinghelper.agent.api.dto.AgentMode;
import com.company.meetinghelper.agent.config.AgentProperties;
import java.util.List;
import org.springframework.stereotype.Service;

/** 执行智能体运行前的硬性权限检查。 */
@Service
public class AgentGuardrailService {
    private static final List<String> WRITE_INTENTS = List.of("保存", "发布", "删除", "恢复版本", "导入", "导出", "落库");
    private final AgentProperties properties;

    /** 创建 guardrail 服务。 *
     * @param properties 智能体配置
     */
    public AgentGuardrailService(AgentProperties properties) {
        this.properties = properties;
    }

    /** 返回请求是否可以进入 provider。 *
     * @param request 聊天请求
     * @return 允许时为空，否则返回拦截原因
     */
    public String blockedReason(AgentChatRequest request) {
        if (!properties.isEnabled()) {
            return "智能体当前未启用。";
        }
        if (request == null || request.mode() != AgentMode.QUERY) {
            return "当前仅支持查询模式。";
        }
        String message = request.message() == null ? "" : request.message();
        return WRITE_INTENTS.stream()
                .filter(message::contains)
                .findFirst()
                .map(intent -> "暂不支持通过智能体执行“" + intent + "”操作，请使用工作台中的人工操作。")
                .orElse(null);
    }
}
