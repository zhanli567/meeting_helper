# ADR 0003：MCP 作为可插拔工具适配层

## 状态

已接受。

## 背景

公司内部平台可能支持注册 MCP，并在 MCP 中配置接口路径。团队目前尚未完全确认内部平台的注册、鉴权、工具 schema 转换和调用细节。

业界通用做法是让工具具备结构化名称、描述、输入 schema 和输出 schema，再由 MCP、Function Calling 或框架 adapter 暴露给模型。

## 决策

本项目先定义自己的 Tool Contract 和 Tool Registry。

MCP 不作为业务代码内部唯一实现，而作为一种外部适配形式：

- 内部平台需要 MCP 时，由 MCP adapter 暴露本项目工具。
- 外部模型需要 function calling 时，由 OpenAI-compatible adapter 暴露本项目工具。
- 本地测试需要 mock 时，由 mock provider 使用同一工具契约。

## 后果

优点：

- 不被某个公司平台实现细节绑定。
- 工具权限、风险等级、审计统一。
- 后续内部平台明确后可快速对接。

代价：

- 需要维护一层 adapter。
- 工具 schema 需要保持严格版本管理。
