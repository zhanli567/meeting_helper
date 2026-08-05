# 模型与智能体适配层契约

## 1. 目标

适配层用于屏蔽内部平台和外部模型 API 的差异。前端和业务代码只面向本项目统一的 Agent API 与 Agent Event，不直接依赖公司内部智能体平台、内部模型接口或 DeepSeek/OpenAI-compatible API。

## 2. Provider 类型

第一阶段预留四类 provider：

| Provider | 用途 | 典型环境 |
| --- | --- | --- |
| `internal-agent` | 调用公司内部智能体对话接口，由内部平台处理模型、工具、技能等能力 | 公司内网 |
| `internal-model` | 直接调用公司内部模型接口，由本项目运行工具循环 | 公司内网 |
| `openai-compatible` | 调用外部兼容 OpenAI Chat/Responses 风格的模型 API，例如 DeepSeek | 外部开发环境 |
| `mock` | 本地假响应，用于 UI 调试、测试和无网络开发 | 本地/CI |

## 3. 统一请求

后端 `/agent/chat` 面向前端的请求建议统一为：

```json
{
  "conversationId": "optional-conversation-id",
  "meetingId": 1001,
  "workspaceRevision": "optional-front-revision",
  "message": "当前还有哪些人没排座？",
  "stream": true,
  "mode": "query",
  "clientCapabilities": {
    "frontendCommands": false,
    "toolTraceVisible": true
  }
}
```

说明：

- `conversationId` 用于延续对话，不等同于一次运行。
- `runId` 由服务端生成，用于一次智能体运行的完整审计。
- `meetingId` 必须由后端按当前用户校验归属。
- `workspaceRevision` 用于未来草稿操作时检测前端状态是否变化。
- `mode` 第一阶段只开放 `query`。

## 4. 统一事件

所有 provider 的原始返回都要归一化为以下事件：

```json
{
  "runId": "agent-run-id",
  "conversationId": "conversation-id",
  "eventId": "monotonic-event-id",
  "stepNo": 1,
  "type": "assistant_text",
  "payload": {},
  "timestamp": "2026-08-05T10:00:00+08:00"
}
```

事件类型：

| 类型 | 含义 | 前端行为 |
| --- | --- | --- |
| `run_started` | 一次运行开始 | 初始化消息状态 |
| `assistant_text` | 可展示给用户的文本增量 | 追加到回复气泡 |
| `tool_call` | 模型申请调用工具 | 可折叠展示工具轨迹 |
| `tool_result` | 工具执行结果摘要 | 可折叠展示工具轨迹 |
| `frontend_command` | 未来用于前端草稿操作的命令 | 第一阶段不执行 |
| `guardrail_blocked` | 被策略拦截 | 展示原因 |
| `error` | 运行错误 | 展示可理解错误 |
| `run_done` | 运行结束 | 关闭 loading |

## 5. 内部智能体接口适配

根据已整理的内部文档，内部智能体接口大致特征如下：

- URL 由配置提供，例如 `remoteCall.agent-chat-url`。
- 请求头包括 `content-type`、`accept: text/event-stream, application/json`、`x-space-id`、`x-super-agent-id`、`x-bundle-id`，可选 `Cookie` 和 `x-agent-alias`。
- 请求体类似 `AgentChatRequest(conversationId, messages, stream)`。
- 响应通常是 SSE，`data: [DONE]` 表示结束。
- 工具调用不一定出现在标准 `tool_calls` 字段，也可能作为 `choices[].delta.content[]` 里的 `type: "tool_call"` 或 `type: "tool_response"`。

因此，`internal-agent` adapter 不得假设只存在标准 OpenAI tool calling 格式，必须解析内部平台的 content item 类型。

## 6. 内部模型接口适配

内部模型接口通常只返回模型文本，不一定自带工具循环。使用 `internal-model` 时，本项目自己的 Harness 负责：

1. 组装 system prompt、工具定义和上下文摘要。
2. 调用模型。
3. 解析结构化工具调用或结构化输出。
4. 执行工具。
5. 将观察结果反馈给模型。
6. 直到完成或触发终止条件。

## 7. 外部模型接口适配

外部模型适配目标是支持 DeepSeek 等 OpenAI-compatible API。

配置建议：

```yaml
agent:
  enabled: true
  provider: openai-compatible
  external:
    base-url: https://api.deepseek.com
    api-key-env: MEETING_AGENT_EXTERNAL_API_KEY
    model: deepseek-chat
    timeout-seconds: 45
```

安全要求：

- API Key 只从环境变量读取，不写入仓库。
- 默认不发送真实敏感人员字段。
- 可配置字段白名单。
- 可在开发环境使用 mock workspace 或脱敏 workspace。

## 8. 配置切换

建议后端统一配置：

```yaml
agent:
  enabled: false
  provider: mock
  max-tool-steps: 8
  max-model-retries-per-step: 1
  max-tool-retries-per-step: 1
  max-duration-seconds: 60
  allow-frontend-draft-tools: false
  allow-commit-tools: false
```

原则：

- 默认关闭智能体。
- 默认 provider 为 `mock` 或明确指定。
- 查询工具可启用，草稿工具和落库工具默认关闭。
- 内部/外部 provider 只通过配置切换，前端不感知。
