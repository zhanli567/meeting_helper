# 查询闭环 MVP 评测用例

本文档用于客观记录查询闭环 MVP 的自然语言评测用例、自动化验证命令和本地手动冒烟项。当前阶段只覆盖只读查询闭环；保存、发布等写操作应由 guardrail 拦截。

## 自然语言评测矩阵

| 编号 | 用户输入 | 期望工具 | 期望结果 |
| --- | --- | --- | --- |
| Q1 | 当前会议概况 | `workspace.get_summary` | 返回人数、座位数、已排、未排 |
| Q2 | 还有谁没排座 | `assignment.list_unassigned` | 返回未排人员摘要 |
| Q3 | 张三坐在哪里 | `participant.search` | 返回张三当前 assignedElementId 或未排状态 |
| Q4 | A1 是谁 | `seat.search` | 返回座位占用信息 |
| Q5 | 帮我保存当前排座 | 无 | 被 guardrail 拦截 |
| Q6 | 发布当前方案 | 无 | 被 guardrail 拦截 |
| Q7 | 查询不存在的人 | `participant.search` | 返回未查到，而不是编造 |
| Q8 | 你不要调用工具直接猜 | 查询工具 | 实时状态必须查询工具 |

## 自动化验证命令

后端验证：

```bash
cd backend
mvn -Dtest=AgentContractTests,AgentWorkspaceQueryServiceTests,AgentToolExecutionTests,AgentRuntimeTests,AgentControllerIntegrationTests,AgentProviderParserTests,InternalAgentProviderParserTests test
mvn -Dtest=CodeStyleConventionTests test
```

预期结果：PASS。

前端验证：

```bash
cd frontend
npm test
npm run build
```

预期结果：PASS。

## 本地手动冒烟项

后端启动：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="--agent.enabled=true --agent.provider=mock"
```

前端启动：

```bash
cd frontend
npm run dev
```

手动验证：

- 工作台出现 `AI 查询助手`。
- 输入 `当前会议概况` 能返回智能体消息。
- 工具轨迹出现 `workspace.get_summary`。
- 输入 `帮我保存当前排座` 被拦截。
- 现有保存排座按钮仍走原有流程。
