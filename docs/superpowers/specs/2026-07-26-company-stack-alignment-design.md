# 公司技术栈与接口规范对齐设计

## 1. 目标

将会议排座系统从当前的 Spring Data JPA、Axios 直连和混合 HTTP 方法实现，整体调整为与 `eval_system` 一致的公司项目结构：

- Java 21，后端统一使用显式类型，不使用 `var`；
- Spring Boot 3.3.5；
- MyBatis-Plus 3.5.10.1；
- Mapper + Repository + Service 分层；
- 正式运行和自动化测试均使用 PostgreSQL；
- 前端通过 Aurora 网络能力访问后端；
- JSON 接口统一返回 `{ code, data, msg }`；
- HTTP 请求只允许 GET 和 POST；
- 用户身份只从公司框架提供的 `CurrentUserHolder` 获取；
- 删除所有演示业务数据初始化代码。

本次仅调整技术栈、工程结构和接口协议，不改变既有排座、场馆、通用人员、动态字段和版本管理业务规则。

## 2. 依赖与运行环境

### 2.1 后端依赖

- Spring Boot Parent 调整为 `3.3.5`；
- Java 保持 `21`；
- 引入 `mybatis-plus-spring-boot3-starter:3.5.10.1`；
- Web 使用 `spring-boot-starter-web`；
- 保留 Validation、PostgreSQL、Lombok、Apache POI 和 PDFBox；
- 删除 Spring Data JPA、JPA 测试依赖和 H2；
- 不引入与当前功能无关的 ORM、迁移或代码生成框架。

Spring Boot 降至 3.3.5 是为了与公司现有项目及 MyBatis-Plus Spring Boot 3 Starter 的验证基线保持一致，避免使用尚未确认兼容性的 Spring Boot 4 组合。

### 2.2 PostgreSQL 数据库

- 业务运行数据库：`meeting_helper`；
- 自动化测试数据库：`meeting_helper_test`；
- 两个数据库都使用 PostgreSQL；
- 用户名和密码继续在 YAML 中明文配置；
- 测试代码只清理 `meeting_helper_test`，不得修改 `meeting_helper`；
- 测试库使用根目录唯一的 `DDL/meeting_helper.sql` 初始化表结构；
- SQL 文件仍只允许 `CREATE TABLE`、`COMMENT ON TABLE` 和 `COMMENT ON COLUMN`；
- 若本次实现需要修改 DDL，必须分别使用完整 DDL 重建两个数据库并验证表结构。

## 3. 后端分层设计

每个业务领域保持以下结构：

```text
领域
├─ api
│  └─ dto
├─ entity
├─ mapper
├─ repository
└─ service
```

### 3.1 Entity

- 移除全部 JPA 注解和 JPA 生命周期回调；
- 使用 MyBatis-Plus 的 `@TableName`、`@TableId`、`@TableField` 和 `@TableLogic`；
- Java 字段与 DDL 列名明确对应；
- ID 继续由应用生成；
- 逻辑删除继续使用 `deleted` 字段；
- 不增加当前版本不需要的乐观锁字段和插件。

### 3.2 Mapper

- 每张业务表都有一个 Mapper；
- Mapper 继承 `BaseMapper<Entity>`；
- 简单单表查询使用 MyBatis-Plus Lambda Wrapper；
- 复杂联表、聚合、分页和 `FOR UPDATE` 悲观锁查询使用 Mapper XML；
- XML 统一放在 `backend/src/main/resources/mapper`；
- Mapper 方法参数使用 `@Param` 明确命名。

### 3.3 Repository

- Repository 改为 `@Repository` 具体类，不再继承 JPA Repository；
- Repository 封装 Mapper 和查询条件，Service 不直接调用 Mapper；
- 保持当前 Service 所需的领域查询接口，降低业务层迁移风险；
- Repository 统一处理逻辑删除、会议范围和用户范围；
- Repository 的公开方法继续提供 Javadoc；
- 批量新增、批量更新和批量删除优先使用 MyBatis-Plus 已有能力，不自行拼接重复 SQL。

### 3.4 Service 与事务

- 继续使用 Spring `@Transactional`；
- 人员新增、导入提交和版本恢复继续在人员读写前获取同一会议的 PostgreSQL 行锁；
- 换座和批量保存继续避免唯一约束的中间冲突；
- Controller、Service 和 Repository 公开方法保留 Javadoc；
- 后端 `src/main` 和 `src/test` 中的局部变量全部使用显式类型；
- 增加规范测试，发现 `var`、JPA 依赖、JPA 注解或缺少 Mapper 时直接失败。

## 4. 用户身份

新增与 `eval_system` 对齐的：

- `CurrentUserHolder`；
- `CurrentUser`。

身份规则：

- 前端不发送 `X-User-Id`；
- 本项目不解析 Cookie；
- 本项目不实现身份拦截器；
- 公司框架负责从 Cookie 解析用户并写入 `CurrentUserHolder`；
- 业务代码从 Holder 读取 `userId` 和用户名；
- Holder 为空时，两者使用空字符串；
- 当前演示阶段所有未接入公司身份框架的请求共享空字符串用户空间；
- 后续公司框架写入真实用户后，现有用户隔离查询自然按真实 `userId` 生效；
- 测试必须在用例前设置 Holder、用例后清理 Holder，防止线程复用造成身份串用。

删除当前自定义的 `CurrentUserProvider`、请求头身份逻辑及对应前端代码。

## 5. 统一响应结构

JSON 接口统一返回：

```json
{
  "code": 0,
  "data": {},
  "msg": "success"
}
```

规则：

- 成功：`code = 0`；
- 失败：`code` 使用稳定的业务或 HTTP 错误码，`msg` 返回可展示信息；
- 无返回数据的操作使用 `data = null`；
- Bean Validation、业务异常和未处理异常均由全局异常处理器转换为统一结构；
- HTTP 状态码仍保留正确语义，前端错误处理同时读取响应状态和 `msg`；
- Excel 模板、Excel 导出和 PDF 导出继续直接返回二进制文件流，不包装 JSON。

统一响应由公共 `ApiResponse<T>` 表达，Controller 明确返回该类型，避免依赖隐式响应改写。

## 6. HTTP 方法与路径

读取和下载使用 GET；创建、更新、删除、发布、恢复和批量保存统一使用 POST。

现有非 GET/POST 接口调整为：

| 操作 | 新接口 |
|---|---|
| 更新场馆 | `POST /venues/{id}/update` |
| 删除场馆 | `POST /venues/{id}/delete` |
| 保存全部排座 | `POST /plans/{planId}/assignments/save` |
| 将人员移回待排 | `POST /plans/{planId}/participants/{participantId}/assignment/delete` |
| 设置排座锁定 | `POST /plans/{planId}/participants/{participantId}/lock` |
| 修改出席状态 | `POST /meetings/{meetingId}/participants/{participantId}/attendance` |
| 将人员移出会议 | `POST /meetings/{meetingId}/participants/{participantId}/delete` |

原本已经使用 GET 或 POST 的接口保持方法和业务含义不变。

增加后端和前端规范测试：

- Controller 不得出现 PUT、PATCH、DELETE 映射；
- 前端请求层不得暴露或调用 PUT、PATCH、DELETE；
- 业务组件不得直接访问 Aurora 或 Axios，只能调用领域 API 模块。

## 7. 前端 Aurora 请求层

### 7.1 Aurora 适配

新增 `frontend/src/api/aurora.js`：

- 公司环境优先使用 `globalThis.Aurora`；
- 外网开发环境使用 Axios 构造兼容的 `service.network`；
- 兼容层仅解决外网运行问题，不模拟登录和用户身份。

### 7.2 HTTP 封装

`frontend/src/api/http.js`：

- 只导出 `get()`、`post()`、`unwrap()` 和文件下载辅助能力；
- 统一处理开发环境 `/api` 代理；
- 不再添加用户请求头；
- `unwrap()` 校验 `{ code, data, msg }`，仅返回 `data`；
- 非零 `code` 或 HTTP 异常统一转换为可展示错误；
- 二进制请求跳过 JSON `unwrap()`，直接返回文件数据。

领域 API 模块负责接口路径和请求参数，Vue 组件不直接调用网络实现。

## 8. 删除初始化数据

删除 `DemoDataInitializer` 及其全部演示业务数据构造逻辑：

- 不自动创建会议；
- 不自动创建人员；
- 不自动创建动态字段和人员记录；
- 不自动创建排座方案、座位分配或版本。

保留 `PresetVenueCatalog`。预置场馆是产品提供的系统能力，不是数据库业务数据初始化，也不会在启动时写入数据库。

系统首次启动后会议列表为空，用户通过预置场馆或自定义场馆自行创建会议。

## 9. 自动化回归测试

### 9.1 PostgreSQL 测试库

- 测试配置连接 `meeting_helper_test`；
- 测试启动前确认当前数据库名严格等于 `meeting_helper_test`，否则拒绝执行清理；
- 使用根目录完整 DDL 初始化测试表；
- 每个测试场景前清理测试库业务数据；
- 并发测试继续使用真实 PostgreSQL 行锁和唯一约束，不再依赖 H2 模拟。

### 9.2 回归范围

保留并迁移现有后端测试，覆盖：

- 场馆、会议、人员和排座；
- 动态字段导入与多记录合并；
- 用户隔离；
- 临时不出席；
- 版本发布和恢复；
- Excel/PDF 导出；
- 同工号并发新增与导入；
- PostgreSQL 唯一约束和悲观锁；
- DDL 注释、表名前缀和唯一 SQL 文件。

前端测试覆盖：

- Aurora 网络封装；
- 统一响应解包；
- GET/POST 方法限制；
- 人员导入、排座、场馆和版本相关 API 路径；
- 既有组件交互。

最终必须通过：

- 后端完整测试；
- 前端完整测试；
- 前端生产构建；
- `git diff --check`；
- PostgreSQL 两个数据库的表结构核验；
- 真实页面核心流程回归。

## 10. 变更与提交

- 每次实现变更在 `CHANGELOG.md` 中使用 `YYYY-MM-DD HH:mm  ` 格式记录；
- Git 提交信息和推送说明使用中文；
- 不提交 `frontend/package-lock.json`；
- 不提交测试报告、构建产物或临时文件；
- 迁移完成并验证后推送 `main`。
