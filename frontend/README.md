# 会议排座助手前端

前端使用 Vue 3、JavaScript、Vite、Vue Router 和 Element Plus。

## 环境要求

- Node.js 20 或更高版本
- npm
- 后端服务默认运行在 `http://localhost:8080`

## 安装依赖

```sh
npm install
```

项目不提交 `package-lock.json`，公共依赖版本已在 `package.json` 中精确固定，以适配公司内部 npm 仓库。

## 本地开发

```sh
npm run dev
```

默认访问地址为 `http://localhost:5173`。如需修改后端地址，可设置 `VITE_API_TARGET`。

## 生产构建

```sh
npm run build
```

构建产物输出到 `dist` 目录。

## 预览构建结果

```sh
npm run preview
```
