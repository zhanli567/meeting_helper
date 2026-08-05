# 上下文与记忆策略

## 1. 问题

智能体任务可能不是一步完成。尤其是自动排座时，模型可能经历“查询座位、查询人员、排一个座位、再查剩余、继续排”的循环。

如果每一步都把完整会场、完整人员、完整座位和完整历史对话塞回模型，会出现：

- 上下文过长。
- token 成本上升。
- 模型遗漏早期信息。
- 工具结果互相污染。
- 长任务后半段质量下降。
- 流式等待时间过长。

因此，本项目不能依赖模型记忆完整状态，而要由系统维护任务状态。

## 2. 状态分层

建议拆成四类状态：

| 状态 | 保存位置 | 进入模型上下文方式 |
| --- | --- | --- |
| 业务真实状态 | 后端数据库/现有 service | 通过工具按需查询 |
| 前端草稿状态 | Vue workspace store | 后续通过 frontend command/draft snapshot 摘要 |
| 智能体运行状态 | Agent Harness | 摘要进入模型 |
| 审计完整日志 | 后端日志/表/文件 | 不完整进入模型，只用于排错 |

## 3. Run 与 Conversation

需要区分：

- `conversationId`：一段对话，可以包含多次用户提问。
- `runId`：一次用户消息触发的一次智能体运行。
- `taskId`：未来复杂排座任务的长期任务标识。

一个 conversation 可以包含多个 run。复杂排座可能由一个 task 跨多个 run 完成。

## 4. 查询阶段上下文

第一阶段查询任务只需要轻量上下文：

- 当前用户身份摘要。
- 当前会议 ID 和名称。
- 当前阶段可用工具列表。
- 当前问题。
- 最近少量对话摘要。
- 必要的工作区摘要。

工作区摘要可以包括：

```json
{
  "meetingId": 1001,
  "meetingName": "月度经营会",
  "participantCount": 120,
  "seatCount": 130,
  "assignedCount": 100,
  "unassignedCount": 20,
  "lockedSeatCount": 5,
  "reservedSeatCount": 3
}
```

不应默认传完整人员列表和完整座位列表。

## 5. 工具结果分页与摘要

所有可能返回大列表的工具都必须支持：

- `limit`
- `cursor` 或分页参数
- `total`
- `items`
- `summary`

例如未排人员工具：

```json
{
  "total": 236,
  "returned": 50,
  "nextCursor": "cursor-2",
  "summary": "共有 236 人未排座，本次返回前 50 人。",
  "items": []
}
```

模型需要更多时必须继续调用工具，而不是一次拿全量。

## 6. 复杂排座上下文

复杂排座阶段建议维护 `agentTaskSession`：

```json
{
  "taskId": "task-001",
  "runId": "run-001",
  "meetingId": 1001,
  "workspaceRevision": "front-rev-10",
  "phase": "planning|awaiting_confirmation|executing|completed|failed",
  "rules": [],
  "planSummary": "",
  "seatSummary": {},
  "participantGroupSummary": {},
  "draftOperations": [],
  "lastToolResults": [],
  "errors": []
}
```

模型每次只拿：

- 当前阶段。
- 规则摘要。
- 剩余待处理摘要。
- 最近工具结果摘要。
- 必要的候选集合。

完整座位和人员明细通过工具按需查询。

## 7. 草稿状态同步

后续前端草稿操作需要避免多标签页或人工操作导致状态错乱。

每个草稿命令应带：

- `workspaceRevision`
- `operationId`
- `expectedParticipantAssignment`
- `expectedSeatOccupant`

前端执行前检查当前状态。如果用户在智能体运行期间手动改了页面，命令应失败并提示重新确认。

## 8. 记忆管理

公司内部平台可能提供记忆能力，但本项目不应把它作为业务正确性的依赖。

可以记忆：

- 用户偏好的表达方式。
- 常用查询习惯。
- 对话摘要。

不应只存在记忆中：

- 当前座位真实状态。
- 当前草稿排座结果。
- 权限信息。
- 发布状态。
- 版本状态。

这些必须来自数据库、前端草稿或 Harness 运行状态。

## 9. 上下文压缩策略

当上下文接近阈值时：

1. 保留系统策略和工具契约。
2. 保留当前用户问题。
3. 保留当前任务状态摘要。
4. 保留最近若干工具结果摘要。
5. 丢弃或压缩旧对话。
6. 大列表只保留统计和 cursor。

压缩不能删除安全策略、权限约束和人工确认要求。
