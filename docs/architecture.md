# 系统架构

## 设计边界

系统核心面向“会议人工排座”，颁奖只是首个场景。场馆空间、参会人员、排座方案与场景业务数据彼此解耦：

- **场馆模板**保存可复用的二维布局；会议创建时复制成布局快照，后续修改模板不会影响历史会议。
- **布局元素**统一表达座位、舞台、走廊、墙、门、楼梯、桌子和标签等对象，并用行、列、跨行、跨列描述占用范围。
- **参会人员**只保存通用身份字段和可扩展属性；奖项、批次等数据由场景记录承载。
- **排座方案**保存当前可编辑状态；**方案版本**保存不可变快照，可从版本历史恢复为新的当前状态，用于回退和防误操作。
- **临时占用**属于排座方案而非场馆模板，既能表示一个人占一个座位，也能表示摄像机等设备占多个布局元素。

```mermaid
erDiagram
    VENUE_TEMPLATE ||--o{ VENUE_ELEMENT : contains
    VENUE_TEMPLATE ||--o{ MEETING : creates
    MEETING ||--o{ MEETING_ELEMENT : snapshots
    MEETING ||--o{ PARTICIPANT : includes
    PARTICIPANT ||--o{ AWARD_RECORD : has
    MEETING ||--|| SEATING_PLAN : owns
    SEATING_PLAN ||--o{ PLAN_ITEM : contains
    PLAN_ITEM ||--o{ PLAN_ITEM_TARGET : occupies
    SEATING_PLAN ||--o{ PLAN_VERSION : snapshots
```

## 导入扩展方式

导入采用“固定流程 + 场景策略 + 字段元数据”：

1. 固定流程负责文件校验、模板识别、重复工号分组、预览和提交。
2. `WorkbookImportStrategy` 负责声明一个场景包含哪些工作表以及如何解析业务记录。
3. 通用人员列写入固定字段，额外列写入人员扩展属性；前端根据返回的字段定义自动生成筛选条件。
4. 新场景通过新增策略注册，不需要修改既有通用会议或颁奖会议解析代码。

当前提供：

- `GENERAL_MEETING_V1`：一个“参会人员”工作表。
- `AWARD_CEREMONY_V1`：“参会人员”与“获奖记录”两个工作表；同一工号的多条获奖记录是正常业务数据。

## 前后端职责

- Vue 工作台负责布局渲染、拖拽交互、筛选视图和当前浏览器会话的撤销/重做。
- Spring Boot 负责所有权威状态、座位容量及交换校验、导入预检、版本快照保存/恢复和文件导出。
- PostgreSQL 保存正式运行数据，Spring Boot SQL 初始化器负责执行统一建表脚本；人员扩展属性、布局样式和版本快照使用 JSON 文本保存，以兼顾结构稳定性和场景扩展性。
- 内存 H2 仅用于自动化测试，不再作为开发或生产运行数据库。

## 后端包结构约定

后端目录对齐公司 `eval_system` 工程，采用“业务模块优先、模块内技术分层”的方式。根包
`com.company.meetinghelper` 下的 `meeting`、`venue`、`participant`、`seating`、
`importing`、`export`、`workspace` 和 `award` 分别代表独立业务边界。

每个模块按实际职责使用以下子包，不为暂时不存在的职责创建空目录：

```text
业务模块
├─ api                         Web 接口
│  └─ dto
│     ├─ request               接口入参
│     └─ response              接口出参
├─ entity                      JPA 实体及与实体紧密相关的枚举
├─ repository                  数据访问接口
└─ service                     业务服务
   ├─ model                    仅在服务内部流转的模型
   └─ strategy                 可替换的场景策略
```

- `common/entity` 保存跨模块实体基类，`common/exception` 保存统一业务异常与异常处理。
- `config` 保存 Spring 配置，`bootstrap` 保存演示数据初始化等应用启动任务。
- 控制器不直接依赖 JPA 实体，接口入参和出参使用 `api/dto` 中的独立类型。
- Service 不再声明供接口使用的嵌套 DTO，避免接口契约与业务实现类耦合。
- `MeetingHelperApplication` 保持在根包，Spring 组件、JPA 实体和 Repository 的默认扫描范围覆盖全部模块。

## DDL 管理约定

- 数据库 SQL 源文件固定为仓库根目录 `DDL/meeting_helper.sql`，构建时由 Maven 复制到应用类路径。
- SQL 文件只允许出现 `CREATE TABLE`、`COMMENT ON TABLE` 和 `COMMENT ON COLUMN`
  语句，不使用 `ALTER`、数据更新语句或独立建索引语句。
- 所有业务表统一使用 `t_` 前缀；JPA 实体通过显式 `@Table` 与 DDL 保持一致。
- 每张表和每个字段都必须提供数据库元数据注释；自动化测试会扫描整个仓库并校验
  SQL 类型、表名前缀和注释覆盖率，避免后续变更重新引入分散脚本或漏写注释。

## 用户隔离接入边界

当前演示版在前端通过独立的会话模块提供当前用户和租户信息，首页只展示“我的会议”入口；它是后续公司统一认证 SDK 的替换点，不把演示用户写散在业务组件中。

正式接入认证时，后端需要从可信令牌或网关上下文解析 `tenantId`、`userId`，并将租户和创建人条件纳入会议、场馆、排座方案及版本的所有查询和写入校验。前端传来的用户标识只能用于展示，不能作为数据权限依据。数据库实体目前已有创建/更新审计字段，租户列和数据权限拦截器在认证 SDK 确定后补齐。

## 第一版明确不做

- 按规则一键排座。
- 上台路径与舞台站位动态计算。
- 圆桌自动生成和环形编号。
- 公司外部人员。
- 多人实时协同和统一认证接入。

这些能力均保留扩展边界，不影响当前人工排座闭环。
