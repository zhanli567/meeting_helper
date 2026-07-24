# 会议排座助手

面向多种会议场景的通用人工排座系统。当前首个落地场景为颁奖会议，系统核心保持场馆、人员、排座方案、导入模板和版本管理的通用抽象。

## 工程结构

- `frontend`：Vue 3 + JavaScript 前端。
- `backend`：Spring Boot 后端，采用业务模块优先、模块内 `api/dto`、`entity`、`repository`、`service` 分层的包结构。
- `DDL`：数据库初始化建表脚本的唯一存放目录。
- `docs/architecture.md`：领域边界、数据关系和扩展点说明。
- `CHANGELOG.md`：持续变更记录。

## 当前可演示能力

- 从首页进入“我的会议”、场馆库或排座工作台；前端已预留当前用户与租户会话边界，便于后续接入公司统一认证。
- 从预置场馆创建会议，或用二维网格设计自定义场馆。
- 下载通用会议/颁奖会议导入模板，预检重复工号后提交导入。
- 搜索、筛选、按导入字段排序、分组及分页浏览待排人员，并保存常用筛选方案。
- 将人员拖入座位、换座、移到空座、撤回待排名单、锁定座位及会话内撤销/重做；拖动时显示可放置和可交换目标。
- 保存不可变的排座版本快照，在版本管理中查看和恢复历史状态，导出 Excel 工作簿或 PDF 场馆图。
- 在固定视口中缩放或拖动画布；人员列表、场馆画布和侧栏分别独立滚动。
- 在场馆设计器中拖动绘制成片元素、框选擦除，并通过元素旁的浮动属性框调整类型、位置、尺寸和颜色。

## 本地开发

环境要求：Java 21、Node.js 20+、PostgreSQL。

先启动后端：

```powershell
cd backend
mvn spring-boot:run
```

再启动前端：

```powershell
cd frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。前端开发服务器会把 `/api` 请求转发到
`http://localhost:8080`。

### 前端

```powershell
cd frontend
npm run build
```

### 后端

```powershell
cd backend
mvn test
```

后端本地配置位于 `backend/src/main/resources/application.yml`，默认连接
`localhost:5432/meeting_helper`，用户名为 `postgres`，密码为 `123456`。
这是当前本地开发约定，不依赖数据库环境变量。Maven 构建时会把根目录
`DDL/meeting_helper.sql` 复制到应用类路径，由 Spring Boot 在启动时执行。
SQL 源文件只允许包含 `CREATE TABLE`，所有表名统一使用 `t_` 前缀。首次启动会自动创建颁奖会议演示数据。

自动化测试继续使用内存 H2，不依赖本机 PostgreSQL。
