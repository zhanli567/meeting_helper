# 智能体查询闭环 MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建设会议排座助手第一阶段智能体能力：用户在工作台用自然语言发起查询，系统通过受控 Agent Harness 调用只读工具并流式返回结果。

**Architecture:** 后端新增独立 `agent` 模块，统一处理配置、Provider 适配、工具注册、Guardrails、运行循环和事件输出。前端新增 `src/agent` 模块，负责聊天面板、SSE 解析和消息状态；`WorkbenchView.vue` 只做最小挂载。第一版先用 `mock` provider 打通本地闭环，再补外部 OpenAI-compatible 和内部平台 provider 适配。

**Tech Stack:** Java 21、Spring Boot 3.3.5、Spring MVC `SseEmitter`、Jackson、Bean Validation、Vue 3、JavaScript、fetch streaming、Node `node:test`、现有 PostgreSQL 集成测试体系。

## Global Constraints

- 分支：`smart`。
- 后端智能体代码只放在 `backend/src/main/java/com/company/meetinghelper/agent/`。
- 前端智能体代码只放在 `frontend/src/agent/`，除 `WorkbenchView.vue` 最小挂载外不污染现有页面。
- 第一阶段只允许 `sideEffect = READ` 的工具。
- 第一阶段不把保存、发布、恢复、删除、导入、导出、落库接口注册成模型可调用工具。
- 前端只调用本项目后端 `/agent/chat`，不直接调用 DeepSeek、OpenAI-compatible API 或公司内部 URL。
- 后端通过配置切换 provider：`mock`、`openai-compatible`、`internal-agent`、`internal-model`。
- 默认配置：`agent.enabled=false`，`agent.provider=mock`。
- 外部 API Key 只从环境变量读取，不写入仓库。
- 本 MVP 不引入 Spring AI、LangGraph 或 MCP 框架依赖；MCP 先作为后续适配层边界保留。
- 所有 provider 原始输出必须归一化为 `AgentEvent` 后再返回前端。
- Java public/protected 类、record、方法遵守现有中文 Javadoc 和方法复杂度约束。
- 每个任务都要先写测试，再实现，再验证，再提交。

---

## 文件结构总览

### 后端新增模块

- `backend/src/main/java/com/company/meetinghelper/agent/config/AgentProperties.java`
  绑定 `agent.*` 配置。

- `backend/src/main/java/com/company/meetinghelper/agent/api/AgentController.java`
  暴露 `POST /agent/chat` SSE 接口。

- `backend/src/main/java/com/company/meetinghelper/agent/api/AgentSseWriter.java`
  把 `AgentEvent` 写成 SSE 格式。

- `backend/src/main/java/com/company/meetinghelper/agent/api/dto/AgentChatRequest.java`
  前端聊天请求。

- `backend/src/main/java/com/company/meetinghelper/agent/api/dto/AgentMode.java`
  当前只支持 `QUERY`。

- `backend/src/main/java/com/company/meetinghelper/agent/runtime/AgentEvent.java`
  后端到前端的统一事件。

- `backend/src/main/java/com/company/meetinghelper/agent/runtime/AgentEventType.java`
  事件类型枚举。

- `backend/src/main/java/com/company/meetinghelper/agent/runtime/AgentRuntime.java`
  Agent Harness 主循环。

- `backend/src/main/java/com/company/meetinghelper/agent/runtime/AgentGuardrailService.java`
  输入、模式、写操作意图、工具步数等约束。

- `backend/src/main/java/com/company/meetinghelper/agent/runtime/AgentTraceLogger.java`
  运行与工具调用日志。

- `backend/src/main/java/com/company/meetinghelper/agent/tool/`
  工具契约、工具注册表、工具执行器。

- `backend/src/main/java/com/company/meetinghelper/agent/tool/query/`
  第一批只读查询工具。

- `backend/src/main/java/com/company/meetinghelper/agent/provider/`
  Provider 抽象、工厂、mock、外部、内部适配。

### 前端新增模块

- `frontend/src/agent/api/agentClient.js`
  使用 fetch 发送聊天请求并读取 SSE。

- `frontend/src/agent/runtime/eventStream.js`
  解析 SSE 半包和完整事件。

- `frontend/src/agent/runtime/messages.js`
  把事件折叠成聊天消息状态。

- `frontend/src/agent/components/AgentChatPanel.vue`
  工作台智能体查询面板。

---

### Task 1: 后端 Agent 配置与事件契约

**Files:**
- Create: `backend/src/main/java/com/company/meetinghelper/agent/config/AgentProperties.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/api/dto/AgentChatRequest.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/api/dto/AgentMode.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/runtime/AgentEvent.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/runtime/AgentEventType.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/MeetingHelperApplication.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application.yml`
- Test: `backend/src/test/java/com/company/meetinghelper/AgentContractTests.java`

**Interfaces:**
- Produces: `AgentProperties`
- Produces: `AgentChatRequest(String conversationId, String meetingId, String workspaceRevision, String message, boolean stream, AgentMode mode)`
- Produces: `AgentEvent(String runId, String conversationId, String eventId, int stepNo, AgentEventType type, Map<String,Object> payload, OffsetDateTime timestamp)`

- [ ] **Step 1: 写失败测试**

```java
package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.meetinghelper.agent.api.dto.AgentMode;
import com.company.meetinghelper.agent.config.AgentProperties;
import com.company.meetinghelper.agent.runtime.AgentEvent;
import com.company.meetinghelper.agent.runtime.AgentEventType;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentContractTests {

    @Test
    void agentPropertiesUseSafeDefaults() {
        AgentProperties properties = new AgentProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getProvider()).isEqualTo("mock");
        assertThat(properties.getMaxToolSteps()).isEqualTo(8);
        assertThat(properties.isAllowFrontendDraftTools()).isFalse();
        assertThat(properties.isAllowCommitTools()).isFalse();
    }

    @Test
    void agentEventHasStableEnvelope() {
        AgentEvent event = new AgentEvent(
                "run-1",
                "conversation-1",
                "event-1",
                1,
                AgentEventType.RUN_STARTED,
                Map.of("mode", AgentMode.QUERY.name()),
                OffsetDateTime.parse("2026-08-05T10:00:00+08:00")
        );

        assertThat(event.runId()).isEqualTo("run-1");
        assertThat(event.type()).isEqualTo(AgentEventType.RUN_STARTED);
        assertThat(event.payload()).containsEntry("mode", "QUERY");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd backend
mvn -Dtest=AgentContractTests test
```

Expected: 编译失败，提示 agent 契约类不存在。

- [ ] **Step 3: 实现配置类**

`AgentProperties` 使用 `@ConfigurationProperties(prefix = "agent")`，默认值：

```java
private boolean enabled = false;
private String provider = "mock";
private int maxToolSteps = 8;
private int maxModelRetriesPerStep = 1;
private int maxToolRetriesPerStep = 1;
private int maxDurationSeconds = 60;
private boolean allowFrontendDraftTools = false;
private boolean allowCommitTools = false;
```

在 `MeetingHelperApplication` 增加 `@ConfigurationPropertiesScan`。

- [ ] **Step 4: 实现请求和事件 DTO**

`AgentMode`:

```java
public enum AgentMode {
    QUERY
}
```

`AgentEventType`:

```java
RUN_STARTED,
ASSISTANT_TEXT,
TOOL_CALL,
TOOL_RESULT,
GUARDRAIL_BLOCKED,
ERROR,
RUN_DONE
```

- [ ] **Step 5: 补充 YAML 配置**

主配置和测试配置都增加：

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

- [ ] **Step 6: 验证**

```bash
cd backend
mvn -Dtest=AgentContractTests test
```

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/company/meetinghelper/agent backend/src/main/java/com/company/meetinghelper/MeetingHelperApplication.java backend/src/main/resources/application.yml backend/src/test/resources/application.yml backend/src/test/java/com/company/meetinghelper/AgentContractTests.java
git commit -m "feat: 新增智能体基础契约"
```

---

### Task 2: 后端只读工作区查询服务

**Files:**
- Create: `backend/src/main/java/com/company/meetinghelper/agent/tool/query/AgentWorkspaceQueryService.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/tool/query/WorkspaceSummaryResult.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/tool/query/ParticipantBrief.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/tool/query/SeatBrief.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/tool/query/AgentPageResult.java`
- Test: `backend/src/test/java/com/company/meetinghelper/AgentWorkspaceQueryServiceTests.java`

**Interfaces:**
- Consumes: `WorkspaceService.getWorkspace(String meetingId)`
- Produces: `WorkspaceSummaryResult summarize(String meetingId)`
- Produces: `AgentPageResult<ParticipantBrief> listUnassigned(String meetingId, String keyword, int limit)`
- Produces: `AgentPageResult<ParticipantBrief> searchParticipants(String meetingId, String keyword, int limit)`
- Produces: `AgentPageResult<SeatBrief> searchSeats(String meetingId, String keyword, int limit)`

- [ ] **Step 1: 写失败测试**

```java
package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.meetinghelper.agent.tool.query.AgentWorkspaceQueryService;
import com.company.meetinghelper.agent.tool.query.WorkspaceSummaryResult;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentWorkspaceQueryServiceTests {

    @Test
    void summarizeCountsWorkspace() {
        AgentWorkspaceQueryService service = new AgentWorkspaceQueryService(meetingId -> workspaceFixture());

        WorkspaceSummaryResult summary = service.summarize("meeting-1");

        assertThat(summary.meetingId()).isEqualTo("meeting-1");
        assertThat(summary.participantCount()).isEqualTo(3);
        assertThat(summary.seatCount()).isEqualTo(2);
        assertThat(summary.assignedCount()).isEqualTo(1);
        assertThat(summary.unassignedCount()).isEqualTo(2);
        assertThat(summary.lockedCount()).isEqualTo(1);
    }

    @Test
    void listUnassignedFiltersByKeyword() {
        AgentWorkspaceQueryService service = new AgentWorkspaceQueryService(meetingId -> workspaceFixture());

        var result = service.listUnassigned("meeting-1", "李", 10);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).extracting("name").containsExactly("李四");
    }

    private static WorkspaceResponse workspaceFixture() {
        return new WorkspaceResponse(
                new WorkspaceResponse.MeetingView("meeting-1", "经营会", "DRAFT", "一号厅", 1, null, "秘书"),
                new WorkspaceResponse.PlanView("plan-1", "默认方案", "DRAFT", 0),
                new WorkspaceResponse.LayoutView(10, 10, List.of(
                        new WorkspaceResponse.ElementView("seat-a1", "SEAT", "A1", 1, 1, 1, 1, "#fff"),
                        new WorkspaceResponse.ElementView("seat-a2", "SEAT", "A2", 1, 2, 1, 1, "#fff")
                )),
                List.of(
                        new WorkspaceResponse.ParticipantView("p1", "001", "张三", Map.of(), Map.of(), List.of(), "PRESENT", true, "seat-a1"),
                        new WorkspaceResponse.ParticipantView("p2", "002", "李四", Map.of("部门", "技术部"), Map.of(), List.of(), "PRESENT", false, null),
                        new WorkspaceResponse.ParticipantView("p3", "003", "王五", Map.of(), Map.of(), List.of(), "ABSENT", false, null)
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd backend
mvn -Dtest=AgentWorkspaceQueryServiceTests test
```

Expected: 编译失败，提示查询服务和结果 record 不存在。

- [ ] **Step 3: 实现结果 record**

核心 record：

```java
public record WorkspaceSummaryResult(
        String meetingId,
        String meetingName,
        int participantCount,
        int attendingCount,
        int seatCount,
        int assignedCount,
        int unassignedCount,
        int lockedCount,
        int availableSeatCount
) {
}
```

```java
public record AgentPageResult<T>(
        int total,
        int returned,
        String summary,
        List<T> items
) {
}
```

- [ ] **Step 4: 实现 `AgentWorkspaceQueryService`**

实现要求：

- Spring 构造器接收 `WorkspaceService`。
- 测试构造器接收 `Function<String, WorkspaceResponse>`。
- `limit` 限制在 `1..100`。
- 查询人员时匹配姓名、工号、出席状态、主动态字段值。
- 查询座位时匹配座位 id、座位名称、占用人姓名、占用人工号。
- 不返回完整 `records`，只返回摘要字段。

- [ ] **Step 5: 验证**

```bash
cd backend
mvn -Dtest=AgentWorkspaceQueryServiceTests test
mvn -Dtest=CodeStyleConventionTests test
```

Expected: 两个命令均 PASS。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/company/meetinghelper/agent/tool/query backend/src/test/java/com/company/meetinghelper/AgentWorkspaceQueryServiceTests.java
git commit -m "feat: 新增智能体只读查询服务"
```

---

### Task 3: 工具注册表与只读工具执行器

**Files:**
- Create: `backend/src/main/java/com/company/meetinghelper/agent/tool/AgentToolDefinition.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/tool/AgentToolSideEffect.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/tool/AgentToolCall.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/tool/AgentToolResult.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/tool/AgentToolRegistry.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/tool/AgentToolExecutor.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/tool/query/AgentQueryTools.java`
- Test: `backend/src/test/java/com/company/meetinghelper/AgentToolExecutionTests.java`

**Interfaces:**
- Produces: `List<AgentToolDefinition> enabledDefinitions()`
- Produces: `AgentToolResult execute(String meetingId, AgentToolCall call)`
- MVP 工具名：
  - `workspace.get_summary`
  - `assignment.list_unassigned`
  - `participant.search`
  - `seat.search`

- [ ] **Step 1: 写失败测试**

```java
package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.meetinghelper.agent.tool.AgentToolCall;
import com.company.meetinghelper.agent.tool.AgentToolExecutor;
import com.company.meetinghelper.agent.tool.AgentToolRegistry;
import com.company.meetinghelper.agent.tool.query.AgentQueryTools;
import com.company.meetinghelper.agent.tool.query.AgentWorkspaceQueryService;
import com.company.meetinghelper.agent.tool.query.WorkspaceSummaryResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentToolExecutionTests {

    @Test
    void registryOnlyExposesReadTools() {
        AgentToolRegistry registry = new AgentToolRegistry();

        assertThat(registry.enabledDefinitions())
                .extracting("name")
                .contains("workspace.get_summary", "assignment.list_unassigned", "participant.search", "seat.search");
        assertThat(registry.enabledDefinitions())
                .allMatch(definition -> "READ".equals(definition.sideEffect().name()));
    }

    @Test
    void executorRunsSummaryTool() {
        AgentWorkspaceQueryService queryService = mock(AgentWorkspaceQueryService.class);
        when(queryService.summarize("meeting-1"))
                .thenReturn(new WorkspaceSummaryResult("meeting-1", "经营会", 2, 2, 3, 1, 1, 0, 2));
        AgentToolExecutor executor = new AgentToolExecutor(new AgentToolRegistry(), new AgentQueryTools(queryService));

        var result = executor.execute("meeting-1", new AgentToolCall("call-1", "workspace.get_summary", Map.of()));

        assertThat(result.success()).isTrue();
        assertThat(result.toolName()).isEqualTo("workspace.get_summary");
    }

    @Test
    void executorRejectsUnknownOrWriteToolName() {
        AgentToolExecutor executor = new AgentToolExecutor(new AgentToolRegistry(), new AgentQueryTools(mock(AgentWorkspaceQueryService.class)));

        var result = executor.execute("meeting-1", new AgentToolCall("call-1", "meeting.delete", Map.of()));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("TOOL_NOT_ALLOWED");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd backend
mvn -Dtest=AgentToolExecutionTests test
```

Expected: 编译失败。

- [ ] **Step 3: 实现工具契约**

`AgentToolSideEffect`:

```java
public enum AgentToolSideEffect {
    READ,
    DRAFT,
    COMMIT,
    EXTERNAL
}
```

`AgentToolCall`:

```java
public record AgentToolCall(String id, String name, Map<String, Object> arguments) {
}
```

`AgentToolResult`:

```java
public record AgentToolResult(
        String callId,
        String toolName,
        boolean success,
        Object data,
        String errorCode,
        String message
) {
}
```

- [ ] **Step 4: 实现 `AgentToolRegistry`**

注册 4 个 READ 工具。每个工具包含：

- `name`
- `description`
- `sideEffect`
- `riskLevel`
- `requiresConfirmation`
- `idempotent`
- `inputSchema`

工具描述必须写清楚“只读，不改变排座结果”。

- [ ] **Step 5: 实现 `AgentToolExecutor`**

执行前校验：

- 工具名存在。
- 工具 `sideEffect == READ`。
- `meetingId` 使用运行时上下文，不信任模型参数。
- `limit` 缺省 50，最大 100。

- [ ] **Step 6: 验证**

```bash
cd backend
mvn -Dtest=AgentToolExecutionTests test
mvn -Dtest=CodeStyleConventionTests test
```

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/company/meetinghelper/agent/tool backend/src/test/java/com/company/meetinghelper/AgentToolExecutionTests.java
git commit -m "feat: 新增智能体只读工具执行器"
```

---

### Task 4: Mock Provider 与 Agent Harness 运行循环

**Files:**
- Create: `backend/src/main/java/com/company/meetinghelper/agent/provider/AgentProvider.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/provider/AgentProviderRequest.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/provider/AgentProviderResponse.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/provider/AgentProviderFactory.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/provider/mock/MockAgentProvider.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/runtime/AgentGuardrailService.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/runtime/AgentRuntime.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/runtime/AgentTraceLogger.java`
- Test: `backend/src/test/java/com/company/meetinghelper/AgentRuntimeTests.java`

**Interfaces:**
- Produces: `AgentProviderResponse next(AgentProviderRequest request)`
- Produces: `void run(AgentChatRequest request, Consumer<AgentEvent> eventSink)`
- Produces: `List<AgentEvent> runOnce(AgentChatRequest request)`

- [ ] **Step 1: 写失败测试**

```java
package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.meetinghelper.agent.api.dto.AgentChatRequest;
import com.company.meetinghelper.agent.api.dto.AgentMode;
import com.company.meetinghelper.agent.config.AgentProperties;
import com.company.meetinghelper.agent.provider.AgentProviderFactory;
import com.company.meetinghelper.agent.provider.mock.MockAgentProvider;
import com.company.meetinghelper.agent.runtime.AgentEventType;
import com.company.meetinghelper.agent.runtime.AgentGuardrailService;
import com.company.meetinghelper.agent.runtime.AgentRuntime;
import com.company.meetinghelper.agent.runtime.AgentTraceLogger;
import com.company.meetinghelper.agent.tool.AgentToolExecutor;
import com.company.meetinghelper.agent.tool.AgentToolRegistry;
import com.company.meetinghelper.agent.tool.query.AgentQueryTools;
import com.company.meetinghelper.agent.tool.query.AgentWorkspaceQueryService;
import com.company.meetinghelper.agent.tool.query.WorkspaceSummaryResult;
import org.junit.jupiter.api.Test;

class AgentRuntimeTests {

    @Test
    void disabledAgentIsBlocked() {
        AgentRuntime runtime = runtime(false);

        var events = runtime.runOnce(new AgentChatRequest(null, "meeting-1", null, "当前会议概况", true, AgentMode.QUERY));

        assertThat(events).extracting("type").contains(AgentEventType.GUARDRAIL_BLOCKED, AgentEventType.RUN_DONE);
    }

    @Test
    void enabledQueryUsesToolAndAnswers() {
        AgentRuntime runtime = runtime(true);

        var events = runtime.runOnce(new AgentChatRequest("c1", "meeting-1", null, "当前会议概况", true, AgentMode.QUERY));

        assertThat(events).extracting("type")
                .contains(AgentEventType.RUN_STARTED, AgentEventType.TOOL_CALL, AgentEventType.TOOL_RESULT, AgentEventType.ASSISTANT_TEXT, AgentEventType.RUN_DONE);
    }

    @Test
    void writeIntentIsBlocked() {
        AgentRuntime runtime = runtime(true);

        var events = runtime.runOnce(new AgentChatRequest("c1", "meeting-1", null, "帮我保存当前排座", true, AgentMode.QUERY));

        assertThat(events).extracting("type").contains(AgentEventType.GUARDRAIL_BLOCKED);
        assertThat(events).noneMatch(event -> event.type() == AgentEventType.TOOL_CALL);
    }

    private static AgentRuntime runtime(boolean enabled) {
        AgentProperties properties = new AgentProperties();
        properties.setEnabled(enabled);
        AgentWorkspaceQueryService queryService = mock(AgentWorkspaceQueryService.class);
        when(queryService.summarize("meeting-1"))
                .thenReturn(new WorkspaceSummaryResult("meeting-1", "经营会", 2, 2, 3, 1, 1, 0, 2));
        AgentToolRegistry registry = new AgentToolRegistry();
        AgentToolExecutor executor = new AgentToolExecutor(registry, new AgentQueryTools(queryService));
        AgentProviderFactory factory = new AgentProviderFactory(properties, new MockAgentProvider());
        return new AgentRuntime(properties, registry, executor, factory, new AgentGuardrailService(properties), new AgentTraceLogger());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd backend
mvn -Dtest=AgentRuntimeTests test
```

Expected: 编译失败。

- [ ] **Step 3: 实现 Provider 抽象**

`AgentProviderResponse` 包含：

- `String assistantText`
- `AgentToolCall toolCall`
- `boolean done`
- `String errorCode`
- `String errorMessage`

- [ ] **Step 4: 实现 `MockAgentProvider`**

规则：

- 用户消息包含 `未排`：首次返回 `assignment.list_unassigned` 工具调用。
- 用户消息包含 `概况` 或 `座位`：首次返回 `workspace.get_summary` 工具调用。
- 用户消息包含 `张`、`李`、`王`、`谁`：首次返回 `participant.search` 工具调用。
- 已有成功工具结果：返回中文总结文本并 `done=true`。
- 无匹配规则：返回 `我可以先帮你查询未排人员、人员位置、座位占用和会议概况。`

- [ ] **Step 5: 实现 Guardrails**

拦截条件：

- `agent.enabled=false`
- `mode != QUERY`
- 用户消息包含：`保存`、`发布`、`删除`、`恢复版本`、`导入`、`导出`、`落库`

拦截事件使用 `GUARDRAIL_BLOCKED`。

- [ ] **Step 6: 实现运行循环**

`run` 流程：

1. 生成 `runId`。
2. 发送 `RUN_STARTED`。
3. 执行 Guardrails。
4. 循环调用 provider。
5. 有工具调用时发送 `TOOL_CALL`，执行工具，发送 `TOOL_RESULT`。
6. 有文本时发送 `ASSISTANT_TEXT`。
7. 超过 `maxToolSteps` 发送 `ERROR`。
8. 发送 `RUN_DONE`。

`runOnce` 用于测试：

```java
List<AgentEvent> events = new ArrayList<>();
run(request, events::add);
return List.copyOf(events);
```

- [ ] **Step 7: 验证**

```bash
cd backend
mvn -Dtest=AgentRuntimeTests test
```

Expected: PASS。

- [ ] **Step 8: 提交**

```bash
git add backend/src/main/java/com/company/meetinghelper/agent/provider backend/src/main/java/com/company/meetinghelper/agent/runtime backend/src/test/java/com/company/meetinghelper/AgentRuntimeTests.java
git commit -m "feat: 新增智能体运行循环"
```

---

### Task 5: 后端 `/agent/chat` SSE 接口

**Files:**
- Create: `backend/src/main/java/com/company/meetinghelper/agent/api/AgentController.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/api/AgentSseWriter.java`
- Test: `backend/src/test/java/com/company/meetinghelper/AgentControllerIntegrationTests.java`

**Interfaces:**
- Consumes: `AgentRuntime.run(AgentChatRequest request, Consumer<AgentEvent> eventSink)`
- Produces: `POST /agent/chat`
- Produces: `text/event-stream`

- [ ] **Step 1: 写失败测试**

```java
package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.meetinghelper.support.PostgreSqlTestDatabaseInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "agent.enabled=true",
        "agent.provider=mock"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgreSqlTestDatabaseInitializer.class)
class AgentControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void agentChatReturnsSseEvents() throws Exception {
        String body = """
                {
                  "conversationId": "c1",
                  "meetingId": "missing-meeting",
                  "message": "当前会议概况",
                  "stream": true,
                  "mode": "QUERY"
                }
                """;

        String response = mockMvc.perform(post("/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).contains("event: run_started");
        assertThat(response).contains("event: run_done");
        assertThat(response).contains("\"runId\"");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd backend
mvn -Dtest=AgentControllerIntegrationTests test
```

Expected: `/agent/chat` 404。

- [ ] **Step 3: 实现 `AgentSseWriter`**

事件格式：

```java
String eventName = event.type().name().toLowerCase(Locale.ROOT);
return SseEmitter.event()
        .name(eventName)
        .data(event);
```

- [ ] **Step 4: 实现 Controller**

使用：

```java
@PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chat(@Valid @RequestBody AgentChatRequest request)
```

内部创建 `SseEmitter`，用 `CompletableFuture.runAsync` 调用：

```java
agentRuntime.run(request, event -> emitter.send(agentSseWriter.toSseEvent(event)));
emitter.complete();
```

异常时发送 `ERROR` 事件并 `completeWithError`。

- [ ] **Step 5: 验证**

```bash
cd backend
mvn -Dtest=AgentControllerIntegrationTests test
mvn -Dtest=AgentContractTests,AgentWorkspaceQueryServiceTests,AgentToolExecutionTests,AgentRuntimeTests,AgentControllerIntegrationTests test
```

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/company/meetinghelper/agent/api backend/src/test/java/com/company/meetinghelper/AgentControllerIntegrationTests.java
git commit -m "feat: 新增智能体查询接口"
```

---

### Task 6: 前端 SSE 解析器与 Agent Client

**Files:**
- Create: `frontend/src/agent/runtime/eventStream.js`
- Create: `frontend/src/agent/runtime/messages.js`
- Create: `frontend/src/agent/api/agentClient.js`
- Test: `frontend/tests/agent-event-stream.test.js`
- Test: `frontend/tests/agent-client-contract.test.js`

**Interfaces:**
- Produces: `parseAgentEventStreamChunk(buffer, chunk)`
- Produces: `reduceAgentMessages(messages, event)`
- Produces: `sendAgentChat({ meetingId, conversationId, message, workspaceRevision, mode, onEvent, signal })`

- [ ] **Step 1: 写失败测试：SSE 半包解析**

```js
import assert from 'node:assert/strict'
import test from 'node:test'

import { parseAgentEventStreamChunk } from '../src/agent/runtime/eventStream.js'

test('解析完整 SSE Agent Event', () => {
  const raw = 'event: assistant_text\\ndata: {"type":"ASSISTANT_TEXT","payload":{"text":"你好"}}\\n\\n'

  const result = parseAgentEventStreamChunk('', raw)

  assert.equal(result.buffer, '')
  assert.equal(result.events.length, 1)
  assert.equal(result.events[0].type, 'ASSISTANT_TEXT')
  assert.equal(result.events[0].payload.text, '你好')
})

test('半包 SSE 会保留到下一次解析', () => {
  const first = parseAgentEventStreamChunk('', 'event: run_done\\ndata: {"type"')
  const second = parseAgentEventStreamChunk(first.buffer, ':"RUN_DONE"}\\n\\n')

  assert.equal(first.events.length, 0)
  assert.equal(second.events[0].type, 'RUN_DONE')
})
```

- [ ] **Step 2: 写失败测试：客户端不直连外部模型**

```js
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('agent client 只调用本项目后端接口', async () => {
  const source = await readFile(new URL('../src/agent/api/agentClient.js', import.meta.url), 'utf8')

  assert.match(source, /fetch\(/)
  assert.match(source, /\/agent\/chat/)
  assert.doesNotMatch(source, /deepseek/i)
  assert.doesNotMatch(source, /openai/i)
})
```

- [ ] **Step 3: 运行测试确认失败**

```bash
cd frontend
npm test -- agent-event-stream.test.js agent-client-contract.test.js
```

Expected: import 失败。

- [ ] **Step 4: 实现 `eventStream.js`**

规则：

- 把 `chunk` 拼到 `buffer` 后按空行切分。
- 完整块解析成事件，不完整尾部继续放在 `buffer`。
- 只解析 `data:` 行。
- JSON 解析失败时返回一个 `ERROR` 事件。

- [ ] **Step 5: 实现 `agentClient.js`**

使用 fetch：

```js
const response = await fetch(apiPath('/agent/chat'), {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
  body: JSON.stringify({ conversationId, meetingId, workspaceRevision, message, stream: true, mode }),
  signal,
})
```

用 `response.body.getReader()` 读取流，`TextDecoder` 解码，交给 `parseAgentEventStreamChunk`，然后逐个调用 `onEvent(event)`。

- [ ] **Step 6: 实现 `messages.js`**

事件折叠规则：

- `RUN_STARTED`：创建一条空助手消息。
- `ASSISTANT_TEXT`：追加文本。
- `TOOL_CALL`、`TOOL_RESULT`：追加到工具轨迹。
- `GUARDRAIL_BLOCKED`、`ERROR`：追加系统提示。
- `RUN_DONE`：把当前助手消息标记为完成。

- [ ] **Step 7: 验证**

```bash
cd frontend
npm test -- agent-event-stream.test.js agent-client-contract.test.js
```

Expected: PASS。

- [ ] **Step 8: 提交**

```bash
git add frontend/src/agent frontend/tests/agent-event-stream.test.js frontend/tests/agent-client-contract.test.js
git commit -m "feat: 新增前端智能体流式客户端"
```

---

### Task 7: 工作台智能体查询面板

**Files:**
- Create: `frontend/src/agent/components/AgentChatPanel.vue`
- Modify: `frontend/src/views/WorkbenchView.vue`
- Test: `frontend/tests/agent-chat-panel-contract.test.js`
- Test: `frontend/tests/workbench-agent-contract.test.js`

**Interfaces:**
- Consumes: `sendAgentChat`
- Consumes: `reduceAgentMessages`
- Produces: `AgentChatPanel` props: `meetingId`、`workspaceRevision`、`disabled`

- [ ] **Step 1: 写失败测试：面板契约**

```js
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('智能体面板明确是查询助手', async () => {
  const source = await readFile(new URL('../src/agent/components/AgentChatPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /AI 查询助手/)
  assert.match(source, /sendAgentChat/)
  assert.match(source, /reduceAgentMessages/)
  assert.match(source, /当前阶段只执行查询/)
  assert.match(source, /工具轨迹/)
})
```

- [ ] **Step 2: 写失败测试：工作台挂载**

```js
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('工作台挂载智能体面板且不传入保存动作', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /AgentChatPanel/)
  assert.match(source, /:meeting-id="workspace\?\.meeting\?\.id"/)
  assert.doesNotMatch(source, /AgentChatPanel[\s\S]*saveCurrentMode/)
  assert.doesNotMatch(source, /AgentChatPanel[\s\S]*createVersion/)
})
```

- [ ] **Step 3: 运行测试确认失败**

```bash
cd frontend
npm test -- agent-chat-panel-contract.test.js workbench-agent-contract.test.js
```

Expected: 文件不存在或断言失败。

- [ ] **Step 4: 实现 `AgentChatPanel.vue`**

MVP UI：

- 右下角浮动入口：`AI 查询助手`。
- 打开后显示聊天窗口。
- 顶部说明：`当前阶段只执行查询，不会保存或发布排座。`
- 支持输入、发送、Enter 提交。
- 流式期间禁用发送按钮。
- 工具轨迹默认折叠。
- 组件卸载时取消正在进行的请求。

- [ ] **Step 5: 在 `WorkbenchView.vue` 挂载**

script 增加：

```js
import AgentChatPanel from '@/agent/components/AgentChatPanel.vue'
```

template 靠近页面末尾增加：

```vue
<AgentChatPanel
  :meeting-id="workspace?.meeting?.id"
  :workspace-revision="String(workspace?.meeting?.layoutVersion || '')"
  :disabled="!workspace?.meeting?.id"
/>
```

- [ ] **Step 6: 验证**

```bash
cd frontend
npm test -- agent-chat-panel-contract.test.js workbench-agent-contract.test.js
npm run build
```

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add frontend/src/agent/components/AgentChatPanel.vue frontend/src/views/WorkbenchView.vue frontend/tests/agent-chat-panel-contract.test.js frontend/tests/workbench-agent-contract.test.js
git commit -m "feat: 在工作台接入智能体查询面板"
```

---

### Task 8: 外部 OpenAI-compatible Provider 适配

**Files:**
- Create: `backend/src/main/java/com/company/meetinghelper/agent/provider/openai/OpenAiCompatibleProvider.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/provider/openai/OpenAiCompatibleResponseParser.java`
- Create: `backend/src/test/resources/agent/openai-tool-call-response.json`
- Create: `backend/src/test/resources/agent/openai-text-response.json`
- Modify: `backend/src/main/java/com/company/meetinghelper/agent/provider/AgentProviderFactory.java`
- Test: `backend/src/test/java/com/company/meetinghelper/AgentProviderParserTests.java`

**Interfaces:**
- Produces: `AgentProviderResponse parse(String json)`
- Provider 只有在 `agent.provider=openai-compatible` 且 API Key 环境变量存在时才发起网络调用。

- [ ] **Step 1: 写失败测试**

```java
package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.meetinghelper.agent.provider.openai.OpenAiCompatibleResponseParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AgentProviderParserTests {

    @Test
    void parsesToolCall() throws Exception {
        String json = Files.readString(Path.of("src/test/resources/agent/openai-tool-call-response.json"));

        var response = new OpenAiCompatibleResponseParser().parse(json);

        assertThat(response.toolCall()).isNotNull();
        assertThat(response.toolCall().name()).isEqualTo("workspace.get_summary");
    }

    @Test
    void parsesAssistantText() throws Exception {
        String json = Files.readString(Path.of("src/test/resources/agent/openai-text-response.json"));

        var response = new OpenAiCompatibleResponseParser().parse(json);

        assertThat(response.assistantText()).contains("当前会议");
        assertThat(response.done()).isTrue();
    }
}
```

- [ ] **Step 2: 新增 fixture**

`openai-tool-call-response.json`:

```json
{
  "choices": [
    {
      "message": {
        "role": "assistant",
        "tool_calls": [
          {
            "id": "call_1",
            "type": "function",
            "function": {
              "name": "workspace.get_summary",
              "arguments": "{}"
            }
          }
        ]
      }
    }
  ]
}
```

`openai-text-response.json`:

```json
{
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "当前会议共有 10 人，8 人已排座。"
      }
    }
  ]
}
```

- [ ] **Step 3: 运行测试确认失败**

```bash
cd backend
mvn -Dtest=AgentProviderParserTests test
```

Expected: 编译失败。

- [ ] **Step 4: 实现解析器**

解析：

- `choices[0].message.tool_calls[0].function.name`
- `choices[0].message.tool_calls[0].function.arguments`
- `choices[0].message.content`

`arguments` 是 JSON 字符串，转成 `Map<String,Object>`。

- [ ] **Step 5: 实现 Provider**

使用 Spring Web 已有能力发 POST 请求。MVP 用非流式模型响应，由本项目后端负责向前端输出统一 SSE。

缺少 API Key 时返回 provider error：

```text
EXTERNAL_API_KEY_MISSING
```

- [ ] **Step 6: 更新 ProviderFactory**

支持：

- `mock`
- `openai-compatible`
- 未识别 provider 回退到 mock 并记录 warning

- [ ] **Step 7: 验证并提交**

```bash
cd backend
mvn -Dtest=AgentProviderParserTests test
git add backend/src/main/java/com/company/meetinghelper/agent/provider backend/src/test/java/com/company/meetinghelper/AgentProviderParserTests.java backend/src/test/resources/agent
git commit -m "feat: 新增外部模型适配器"
```

---

### Task 9: 内部智能体/模型 Provider 解析与适配

**Files:**
- Create: `backend/src/main/java/com/company/meetinghelper/agent/provider/internal/InternalAgentProvider.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/provider/internal/InternalAgentSseParser.java`
- Create: `backend/src/main/java/com/company/meetinghelper/agent/provider/internal/InternalModelProvider.java`
- Create: `backend/src/test/resources/agent/internal-agent-tool-call.sse`
- Create: `backend/src/test/resources/agent/internal-agent-text.sse`
- Modify: `backend/src/main/java/com/company/meetinghelper/agent/provider/AgentProviderFactory.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/company/meetinghelper/InternalAgentProviderParserTests.java`

**Interfaces:**
- Produces: `List<AgentProviderResponse> parse(String sseText)`
- Supports: 内部 SSE 的 `delta.content[]` 中 `type=text/tool_call/tool_response/error`

- [ ] **Step 1: 写失败测试**

```java
package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.meetinghelper.agent.provider.internal.InternalAgentSseParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InternalAgentProviderParserTests {

    @Test
    void parsesToolCallFromDeltaContent() throws Exception {
        String sse = Files.readString(Path.of("src/test/resources/agent/internal-agent-tool-call.sse"));

        var responses = new InternalAgentSseParser().parse(sse);

        assertThat(responses).anyMatch(response ->
                response.toolCall() != null
                        && "workspace.get_summary".equals(response.toolCall().name()));
    }

    @Test
    void parsesTextAndDone() throws Exception {
        String sse = Files.readString(Path.of("src/test/resources/agent/internal-agent-text.sse"));

        var responses = new InternalAgentSseParser().parse(sse);

        assertThat(responses).anyMatch(response -> response.assistantText().contains("当前会议"));
        assertThat(responses).anyMatch(response -> response.done());
    }
}
```

- [ ] **Step 2: 新增内部 SSE fixture**

`internal-agent-tool-call.sse`:

```text
data: {"choices":[{"delta":{"content":[{"type":"tool_call","name":"workspace.get_summary","id":"call_1","arguments":"{}"}]}}]}

data: [DONE]
```

`internal-agent-text.sse`:

```text
data: {"choices":[{"delta":{"content":[{"type":"text","text":"当前会议共有 10 人。"}]}}]}

data: [DONE]
```

- [ ] **Step 3: 运行测试确认失败**

```bash
cd backend
mvn -Dtest=InternalAgentProviderParserTests test
```

Expected: 编译失败。

- [ ] **Step 4: 实现 `InternalAgentSseParser`**

规则：

- 逐行读取 `data:`。
- 空行忽略。
- `[DONE]` 转成 done response。
- 解析 `choices[].delta.content[]`。
- `tool_call` 转 `AgentToolCall`。
- `tool_response` 作为观察文本记录，不直接执行。
- 未知类型只在有安全文本字段时转成 assistant text。

- [ ] **Step 5: 实现内部 Provider**

`InternalAgentProvider`：

- URL 来自 `agent.internal.agent-chat-url`。
- Header 支持 `x-space-id`、`x-super-agent-id`、`x-bundle-id`、可选 `x-agent-alias`。
- Body 使用 `conversationId`、`messages`、`stream=true`。
- 响应交给 `InternalAgentSseParser`。

`InternalModelProvider`：

- URL 来自 `agent.internal.model-url`。
- IAM token 从 `agent.internal.iam-token-env` 指定的环境变量读取。
- Body 包含 `model`、`messages`、`stream=false`。
- 解析 `choices[0].message.content`。

- [ ] **Step 6: 补充配置**

```yaml
agent:
  internal:
    agent-chat-url: ''
    model-url: ''
    model: ''
    iam-token-env: MEETING_AGENT_INTERNAL_IAM_TOKEN
    space-id: ''
    super-agent-id: ''
    bundle-id: ''
    agent-alias: ''
  external:
    base-url: ''
    api-key-env: MEETING_AGENT_EXTERNAL_API_KEY
    model: deepseek-chat
```

- [ ] **Step 7: 验证并提交**

```bash
cd backend
mvn -Dtest=InternalAgentProviderParserTests,AgentProviderParserTests test
git add backend/src/main/java/com/company/meetinghelper/agent/provider backend/src/main/resources/application.yml backend/src/test/java/com/company/meetinghelper/InternalAgentProviderParserTests.java backend/src/test/resources/agent
git commit -m "feat: 新增内部智能体平台适配器"
```

---

### Task 10: 查询闭环评测文档与最终验证

**Files:**
- Create: `docs/agent/08-query-mvp-evaluation.md`
- Modify: `docs/agent/README.md`

**Interfaces:**
- Produces: 查询闭环评测矩阵。
- Produces: 最终验证记录。

- [ ] **Step 1: 新增评测文档**

`docs/agent/08-query-mvp-evaluation.md` 内容：

```markdown
# 查询闭环 MVP 评测用例

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
```

- [ ] **Step 2: 更新文档索引**

在 `docs/agent/README.md` 增加：

```markdown
- [08-query-mvp-evaluation.md](08-query-mvp-evaluation.md)：查询闭环 MVP 的自然语言评测用例。
```

- [ ] **Step 3: 后端验证**

```bash
cd backend
mvn -Dtest=AgentContractTests,AgentWorkspaceQueryServiceTests,AgentToolExecutionTests,AgentRuntimeTests,AgentControllerIntegrationTests,AgentProviderParserTests,InternalAgentProviderParserTests test
mvn -Dtest=CodeStyleConventionTests test
```

Expected: PASS。

- [ ] **Step 4: 前端验证**

```bash
cd frontend
npm test
npm run build
```

Expected: PASS。

- [ ] **Step 5: 本地冒烟验证**

后端：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="--agent.enabled=true --agent.provider=mock"
```

前端：

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

- [ ] **Step 6: 提交**

```bash
git add docs/agent/README.md docs/agent/08-query-mvp-evaluation.md
git commit -m "docs: 新增智能体查询闭环评测用例"
```

---

## Self-Review Checklist

- [ ] 设计覆盖：查询先行、Provider 适配、MCP 可插拔边界、Guardrails、上下文控制、流式事件归一化、观测评测均有对应任务。
- [ ] 占位词扫描：计划中不存在未决占位内容。
- [ ] 类型一致性：Task 1 到 Task 7 的前后端接口名保持一致。
- [ ] 安全检查：没有任何任务把保存、发布、删除、恢复、导入、导出注册为模型工具。
- [ ] 环境检查：外部和内部 Provider 均有 fixture 解析测试，不要求 CI 访问公司内网或真实外部 API。
