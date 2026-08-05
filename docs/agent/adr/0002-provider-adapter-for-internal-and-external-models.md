# ADR 0002：通过 Provider Adapter 支持内外模型切换

## 状态

已接受。

## 背景

公司内部有智能体平台和模型接口，但外部开发环境无法访问内网能力。外部开发需要使用 DeepSeek 等公开 API 或 mock provider 模拟智能体能力。

不同 provider 的协议、流式格式、工具调用格式和认证方式不同。

## 决策

前端只调用本项目后端的统一智能体接口。

后端通过 provider adapter 支持：

- `internal-agent`
- `internal-model`
- `openai-compatible`
- `mock`

所有 provider 输出统一转换为 Agent Event。

## 后果

优点：

- 前端不关心内部/外部差异。
- 内网平台变化时只调整 adapter。
- 外部开发可继续推进。
- 便于测试和回放。

代价：

- 后端需要维护事件归一化层。
- 内部平台特殊 SSE 格式需要单独适配。
