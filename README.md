# 会议排座助手

面向多种会议场景的通用人工排座系统。当前首个落地场景为颁奖会议，系统核心保持场馆、人员、排座方案、导入模板和版本管理的通用抽象。

## 工程结构

- `frontend`：Vue 3 + TypeScript 前端。
- `backend`：Spring Boot 后端。
- `CHANGELOG.md`：持续变更记录。

## 本地开发

### 前端

```bash
cd frontend
npm install
npm run dev
```

### 后端

```bash
cd backend
./mvnw spring-boot:run
```

后端默认使用本地 H2 数据库，首次启动会自动创建演示数据。

