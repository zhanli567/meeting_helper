# 会议排座助手

面向公司内部会议组织场景的单体应用。系统以全局场馆模板库为基础创建会议，并在创建时复制独立的会场布局快照，避免模板后续变化影响历史会议。

## 工程结构

- `frontend/`：Vue 3 + JavaScript + Element Plus 2.8 前端。
- `backend/`：Java 21 + Spring Boot + MyBatis-Plus 后端。
- `backend/src/main/resources/db/ddl.sql`：PostgreSQL 正式建表脚本。
- `docs/architecture.md`：当前架构与关键设计决策。
- `CHANGELOG.md`：版本变更记录。

## 当前能力

- 在“我的会议”“场馆模板”“参会工作台”之间导航。
- 浏览、搜索、分页和按园区分组查看所有用户共享的场馆模板。
- 通过“基础信息 + 布局编辑”两步流程新建场馆模板，也可继续编辑或物理删除已有模板。
- 在二维布局编辑器中放置座位与通用元素，支持点击/框选、拖动、八方向缩放、画布边缘与角点扩展、右键平移、滚轮缩放、撤销/重做、属性编辑与冲突提示。
- 对布局座位数与人工容纳人数不一致给出软提示，不阻止保存。
- 从场馆模板创建会议时复制独立快照；之后编辑或删除模板不会改变已创建会议的布局。
- 管理参会人、座位分配、临时占座、锁座、提案与提交冲突。

## 本地开发

### 前置条件

- Java 21
- Maven 3.9+
- Node.js 20+
- PostgreSQL 14+，本地默认连接为 `localhost:5432`

### 建立开发数据库

1. 创建数据库 `meeting_helper`。
2. 使用 PostgreSQL 账号 `postgres`、密码 `123456`，执行 `backend/src/main/resources/db/ddl.sql`。

项目不包含代码内置场馆、演示数据或启动时写库逻辑；正式 DDL 也不包含种子数据、外键和软删除列。需要不同连接信息时，请通过 Spring 配置覆盖默认值。

### 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认监听 `http://localhost:8080`。

### 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认监听 `http://localhost:5173`，并将 `/api` 请求代理到后端。

## 验证

### 后端

后端测试仅使用 PostgreSQL。请先确保本机 PostgreSQL 已启动，并保证 `postgres` / `123456` 账号具有创建测试数据库和重建 schema 的权限。

```bash
cd backend
mvn test
```

测试会按需创建专用数据库 `meeting_helper_test`，并重建其中的 `public` schema。不要将真实数据放入该测试数据库。

### 前端

```bash
cd frontend
npm test
npm run build
```

## 数据与隔离约束

- 场馆模板是全局共享资源，不按用户隔离。
- 会议、参会人和操作工作台按当前用户隔离。
- 会议保存模板信息与布局的独立快照。
- 数据采用物理删除；关联清理由应用事务显式完成。
- 数据库不声明外键，业务一致性由服务层校验、事务和并发控制保证。
