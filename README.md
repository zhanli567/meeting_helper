# 会议排座助手

面向多种会议场景的通用人工排座系统。当前首个落地场景为颁奖会议，系统核心保持场馆、人员、排座方案、导入模板和版本管理的通用抽象。

## 工程结构

- `frontend`：Vue 3 + TypeScript 前端。
- `backend`：Spring Boot 后端。
- `docs/architecture.md`：领域边界、数据关系和扩展点说明。
- `CHANGELOG.md`：持续变更记录。

## 当前可演示能力

- 从预置场馆创建会议，或用二维网格设计自定义场馆。
- 下载通用会议/颁奖会议导入模板，预检重复工号后提交导入。
- 搜索、筛选、排序、分组待排人员，并保存常用视图。
- 将人员拖入座位、换座、移到空座、撤回待排名单、锁定座位及会话内撤销/重做。
- 保存不可变的排座版本快照，导出 Excel 工作簿或 PDF 场馆图。

## 本地开发

环境要求：Java 21、Node.js 22.18+。

先启动后端：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
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
npm run lint
npm run build
npm run test:unit -- --run
```

### 后端

```powershell
cd backend
.\mvnw.cmd test
```

后端默认使用 `backend/data` 下的本地 H2 数据库，Flyway 管理数据库结构；首次启动会自动创建颁奖会议演示数据。生产环境接入公司数据库和统一认证时，只需替换运行配置和安全适配层。
