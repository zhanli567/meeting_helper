# 智能体模块文档索引

本目录用于维护会议排座助手接入智能体能力时的设计、约束、接口和排错依据。

这里的核心原则是：模型负责理解和推理，系统负责约束和执行。智能体能力不能直接散落在前端页面、后端业务服务或 prompt 中，而应通过一层可替换、可审计、可回退的 Agent Harness 统一驾驭。

## 当前建设阶段

第一阶段只打通查询闭环：

1. 用户在前端对话窗口输入自然语言。
2. 前端调用本系统后端的智能体接口。
3. 后端根据配置选择内部智能体、内部模型、外部模型或 mock provider。
4. 模型通过结构化工具调用完成只读查询。
5. 系统将查询结果流式返回给前端。
6. 全链路记录 runId、工具调用、参数摘要、结果摘要和错误信息。

草稿排座、批量排座、保存发布等能力后续分阶段开放。

## 文档结构

- [00-agent-vision-and-scope.md](00-agent-vision-and-scope.md)：智能体目标、范围和分阶段路线。
- [01-provider-adapter-contract.md](01-provider-adapter-contract.md)：内部/外部模型与智能体接口的统一适配契约。
- [02-agent-harness-runtime.md](02-agent-harness-runtime.md)：Agent Harness 运行时、循环、重试、终止条件。
- [03-tool-registry-and-mcp-contract.md](03-tool-registry-and-mcp-contract.md)：工具注册、MCP 适配、工具原子化约束。
- [04-guardrails-and-permission-policy.md](04-guardrails-and-permission-policy.md)：权限、安全、人工确认、外部模型脱敏策略。
- [05-context-and-memory-strategy.md](05-context-and-memory-strategy.md)：长任务上下文、记忆、摘要、分页和草稿状态。
- [06-observability-and-incident-playbook.md](06-observability-and-incident-playbook.md)：观测、追踪、评测和问题排查。
- [07-framework-and-harness-position.md](07-framework-and-harness-position.md)：Agent Harness、MCP、Spring AI、LangGraph 等框架和范式在本项目中的定位。
- [08-query-mvp-evaluation.md](08-query-mvp-evaluation.md)：查询闭环 MVP 的自然语言评测用例。
- [adr/](adr/)：关键架构决策记录。

## 参考资料

- Anthropic: [Building effective agents](https://www.anthropic.com/engineering/building-effective-agents)
- OpenAI: [Agents guide](https://developers.openai.com/api/docs/guides/agents)
- OpenAI: [Guardrails and human review](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals)
- OpenAI: [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- Spring AI: [Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- Spring AI: [MCP Overview](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)
- MCP: [Security Best Practices](https://modelcontextprotocol.io/docs/draft/tutorials/security/security_best_practices)
- OWASP: [Top 10 for LLM Applications](https://owasp.org/www-project-top-10-for-large-language-model-applications/)
- OWASP: [MCP Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/MCP_Security_Cheat_Sheet.html)
- LangGraph: [Human-in-the-loop](https://docs.langchain.com/oss/python/langchain/human-in-the-loop)
- OpenTelemetry: [GenAI observability](https://opentelemetry.io/blog/2026/genai-observability/)
